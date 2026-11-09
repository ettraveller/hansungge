package com.hanseongge.domain.user.identity;

import com.hanseongge.global.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import mup.mcash.module.common.McashCipher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * KG파이낸셜(mcash) 휴대폰본인확인 — 웹링크 연동.
 *
 * <p>규격: 가맹점 관리자({@code cp.mcash.co.kr}) > 기술지원 > 결제모듈다운로드 >
 * {@code MOBIL_PHONE_AUTH_0001} 연동매뉴얼, 샘플 {@code CI_WEB_NEW_0001}.
 *
 * <h2>이니시스와 무엇이 다른가</h2>
 * 통합인증(이니시스)은 결과가 오면 <b>우리가 되물어</b> 신원을 가져온다. mcash 는 공급자가
 * 우리를 <b>두 번 부른다</b>.
 * <ol>
 *   <li><b>인증요청</b> — 브라우저가 {@code auth.mobilians.co.kr/goAuthMain.mcash} 로 폼 POST
 *   <li>(사용자가 문자·PASS·QR 중 하나로 인증을 마친다 — 무엇을 쓸지는 그 창 안에서 고른다)
 *   <li><b>노티</b> — mcash 서버가 {@code Notiurl} 로 이름·생년월일·휴대폰·CI·DI 를 보낸다.
 *       우리는 {@code SUCCESS} 를 출력해야 하고, <b>이 호출은 단 한 번뿐이다</b>
 *   <li><b>화면 전환</b> — 사용자의 브라우저가 {@code Okurl} 로 돌아온다. 여기에는 신원이 없다
 * </ol>
 * 그래서 {@link #verify} 는 밖으로 나가지 않는다. 3번에서 받아 둔 신원을 거래번호로 찾는다
 * ({@link McashNotiStore}).
 *
 * <h2>지키는 것</h2>
 * <ul>
 *   <li>두 경로 모두 {@code Mac}(SHA-256)을 먼저 검증한다. 노티는 외부에서 아무나 부를 수 있는
 *       주소이고, Okurl 값은 브라우저를 거쳐 온다 — 둘 다 그대로 믿으면 남의 신원을 밀어 넣을 수 있다
 *   <li>Mac 재료에 가맹점 고유키가 들어간다. 키를 모르면 맞출 수 없다
 *   <li>돌아온 거래번호가 우리가 발급한 것인지 대조한다
 *   <li>CI 원본은 결과 객체로만 넘긴다. 로그에 남기지 않는다
 * </ul>
 *
 * <h2>암호화</h2>
 * 모든 값이 SEED 로 오간다. 키는 <b>가맹점 고유키 8자리 + 서비스ID 앞 8자리</b> = 16바이트,
 * 평문은 EUC-KR, 암호문은 hex 다. 구현은 공급자 모듈({@code McashCipher})을 그대로 쓴다 —
 * 같은 SEED 라도 이니시스 것({@link InicisSeedCipher})과 결과가 다르다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.identity.provider", havingValue = McashIdentityVerificationProvider.CODE)
public class McashIdentityVerificationProvider implements IdentityVerificationProvider {

    public static final String CODE = "MCASH";

    /** 매뉴얼 5.1 공통 규약. {@code PAY_MODE} 가 어느 쪽으로 갈지를 정한다. */
    private static final String LIVE_URL = "https://auth.mobilians.co.kr/goAuthMain.mcash";
    private static final String TEST_URL = "https://authtest.mobilians.co.kr/goAuthMain.mcash";
    private static final String LIVE_MODE = "10";

    private static final String SUCCESS = "0000";
    /** 매뉴얼 5.2 "CI_Mode". 61 = 기본인증방식 — 인증 수단은 창 안에서 사용자가 고른다. */
    private static final String DEFAULT_CI_MODE = "61";
    /** 생년월일은 {@code 19800101} 로 온다. */
    private static final DateTimeFormatter BIRTH = DateTimeFormatter.BASIC_ISO_DATE;
    /** 샘플(okurl.jsp·notiurl.jsp)이 쓰는 문자셋. Mac 재료를 만들 때도 이 값이다. */
    private static final String CHARSET = "EUC-KR";

    private final AppProperties.Identity config;
    private final McashNotiStore notiStore;
    private final String cryptKey;

    McashIdentityVerificationProvider(AppProperties appProperties, McashNotiStore notiStore) {
        this.config = appProperties.identity();
        this.notiStore = notiStore;
        this.cryptKey = cryptKey(config);

        if (!isConfigured()) {
            log.warn("""
                    [IDENTITY] IDENTITY_PROVIDER=MCASH 인데 서비스ID/암호화키가 비어 있습니다. 자체 가입이 닫힙니다.
                    IDENTITY_MID(서비스ID) / IDENTITY_API_KEY(암호화키 8자리) 를 주입하세요.""");
        }
    }

    /**
     * 복호화 키 = 가맹점 고유키(8바이트) + 서비스ID 앞 8자리 = 16바이트 (매뉴얼 5.4).
     *
     * <p>둘 중 하나라도 짧으면 만들지 않는다 — 잘못 잘린 키로 돌면 복호화가 조용히 깨지고,
     * 그 원인을 운영 로그에서 찾아내기 어렵다.
     */
    private static String cryptKey(AppProperties.Identity config) {
        String key = config.apiKey().strip();
        String svcId = config.mid().strip();
        if (key.length() < 8 || svcId.length() < 8) {
            return "";
        }
        return key.substring(0, 8) + svcId.substring(0, 8);
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean isConfigured() {
        return !cryptKey.isEmpty() && !config.notiUrl().isBlank();
    }

    /**
     * 수단을 가리지 않는다.
     *
     * <p>이니시스는 간편인증 창과 휴대폰 창이 따로 있어 우리가 골라 열었다. mcash 는 창이 하나고,
     * 문자·PASS·QR 중 무엇으로 할지는 그 안에서 사용자가 고른다. 그래서 여기서 막을 것이 없다.
     */
    @Override
    public boolean supports(IdentityMethod method) {
        return true;
    }

    @Override
    public IdentityStartForm start(String requestId, String callbackUrl, IdentityMethod method) {
        String payMode = config.payMode().strip();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("CASH_GB", "CI");                 // 고정값, 수정불가
        fields.put("CI_SVCID", config.mid().strip());
        fields.put("PAY_MODE", payMode);
        fields.put("Cryptyn", "Y");
        fields.put("Keygb", config.keygb().strip());
        fields.put("Siteurl", config.siteUrl().strip());
        fields.put("Tradeid", requestId);
        fields.put("Okurl", encrypt(callbackUrl));
        fields.put("Notiurl", encrypt(config.notiUrl().strip()));
        fields.put("Cryptstring", encrypt(requestId + callbackUrl));
        fields.put("CI_Mode", DEFAULT_CI_MODE);
        // 이동통신사 정책상 가맹점 로고를 띄울 수 없다. 매뉴얼이 "N" 고정으로 못박았다.
        fields.put("LOGO_YN", "N");
        // 페이지 전환. 팝업(기본값)으로 열면 차단기에 걸리는 브라우저가 있고,
        // 이니시스도 같은 방식이라 화면 쪽 분기가 늘지 않는다.
        fields.put("CALL_TYPE", "SELF");
        fields.put("Sendtype", "POST");
        // 한글 필드(SMS·LMS 문구)는 쓰지 않는다. 폼이 EUC-KR 로 전송되는데 값이 전부
        // ASCII 면 화면이 문자셋을 신경 쓸 일이 없다.
        return new IdentityStartForm(LIVE_MODE.equals(payMode) ? LIVE_URL : TEST_URL, "POST", fields);
    }

    /**
     * 화면 전환(Okurl)으로 돌아온 값을 검증하고, 노티로 받아 둔 신원을 꺼낸다.
     *
     * <p>Okurl 에는 신원이 없다 — 결과코드와 거래번호뿐이다. 신원은 {@link #acceptNoti} 가
     * 먼저 받아 두었다.
     */
    @Override
    public IdentityVerificationResult verify(String requestId, Map<String, String> returned) {
        // 실패하면 평문으로 오는 값이 있어(매뉴얼 5.2 "응답 명세서") 복호화를 강요하지 않는다.
        String resultCode = decryptOrRaw(returned.get("Resultcd"));
        if (!SUCCESS.equals(resultCode)) {
            throw new IdentityVerificationException("mcash 인증 실패 Resultcd=" + resultCode
                    + " msg=" + decryptOrRaw(returned.get("Resultmsg")));
        }

        String tradeId = decrypt(returned.get("Tradeid"), "Tradeid");
        String svcId = decrypt(returned.get("Svcid"), "Svcid");
        String mobilId = decrypt(returned.get("Mobilid"), "Mobilid");
        String signDate = decrypt(returned.get("Signdate"), "Signdate");

        // 매뉴얼 5.4 / okurl 샘플: SHA256(Tradeid + Svcid + Mobilid + Signdate + 비밀키)
        requireMac(returned.get("Mac"), tradeId + svcId + mobilId + signDate + cryptKey, "Okurl");

        if (!requestId.equals(tradeId)) {
            throw new IdentityVerificationException("mcash 거래번호 불일치 — 다른 요청의 인증 결과");
        }

        IdentityVerificationResult result = notiStore.take(tradeId)
                // 노티가 먼저 와야 한다. 못 찾는 경우는 노티 실패(Resultcd 9997)거나
                // 그 사이 서버가 재시작된 것이다 — 둘 다 다시 인증받는 수밖에 없다.
                .orElseThrow(() -> new IdentityVerificationException(
                        "mcash 인증 결과를 찾지 못했습니다 — 노티 미수신 tradeId=" + tradeId));

        log.info("[IDENTITY] mcash 인증 통과. tradeId={} mobilId={} ci={} di={}",
                tradeId, mobilId,
                result.ci() == null || result.ci().isBlank() ? "없음" : "있음",
                result.di() == null || result.di().isBlank() ? "없음" : "있음");
        return result;
    }

    /**
     * 노티(서버 간 호출)를 받는다.
     *
     * <p>mcash 는 이 호출을 <b>한 번만</b> 한다. 여기서 던지면 그 사람은 인증을 다시 해야 하므로
     * 예외를 밖으로 내보내지 않고 성패만 돌려준다 — 호출한 쪽이 {@code SUCCESS}/{@code FAIL} 를
     * 출력한다.
     *
     * @return 받아들였으면 true. {@code false} 면 위·변조이거나 복호화가 깨진 것이다
     */
    boolean acceptNoti(Map<String, String> params) {
        try {
            String resultCode = decryptOrRaw(params.get("Resultcd"));
            String tradeId = decrypt(params.get("Tradeid"), "Tradeid");
            String signDate = decrypt(params.get("Signdate"), "Signdate");
            String mobilId = decrypt(params.get("Mobilid"), "Mobilid");

            String name = decryptOrRaw(params.get("Name"));
            String phone = decryptOrRaw(params.get("No"));
            String carrier = decryptOrRaw(params.get("Commid"));
            String birth = decryptOrRaw(params.get("Socialno"));
            String sex = decryptOrRaw(params.get("Sex"));
            String foreigner = decryptOrRaw(params.get("Foreigner"));
            String ci = decryptOrRaw(params.get("Ci"));
            String di = decryptOrRaw(params.get("Di"));

            // 매뉴얼 5.4 / notiurl 샘플:
            // SHA256(urlencode(Name+No+Socialno+Commid+Sex+Foreigner, EUC-KR)
            //        + Tradeid + Signdate + Di + Ci + Mobilid + 비밀키)
            String userInfo = name + phone + birth + carrier + sex + foreigner;
            String material = urlEncode(userInfo) + tradeId + signDate + di + ci + mobilId + cryptKey;
            requireMac(params.get("Mac"), material, "Notiurl");

            if (!SUCCESS.equals(resultCode)) {
                // 실패 노티도 정상 수신이다. 사용자는 Okurl 로 돌아와 그 결과코드를 보게 된다.
                log.info("[IDENTITY] mcash 실패 노티. tradeId={} Resultcd={}", tradeId, resultCode);
                return true;
            }

            notiStore.put(tradeId, new IdentityVerificationResult(
                    mobilId, ci, di, name, parseBirth(birth), phone, carrier));
            log.info("[IDENTITY] mcash 노티 수신. tradeId={} mobilId={} 통신사={}", tradeId, mobilId, carrier);
            return true;
        } catch (IdentityVerificationException e) {
            // 평문 조각이 섞일 수 있어 메시지만 남긴다.
            log.warn("[IDENTITY] mcash 노티를 받아들이지 못했다: {}", e.getMessage());
            return false;
        }
    }

    private static LocalDate parseBirth(String value) {
        try {
            return LocalDate.parse(value, BIRTH);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new IdentityVerificationException("mcash 생년월일 형식 오류", e);
        }
    }

    private String encrypt(String plain) {
        return McashCipher.encodeString(plain, cryptKey);
    }

    private String decrypt(String cipherText, String field) {
        String value = decryptOrRaw(cipherText);
        if (value == null || value.isBlank()) {
            throw new IdentityVerificationException("mcash 응답에 " + field + " 가 없습니다");
        }
        return value;
    }

    /** 복호화해 보고, 안 되면 받은 값을 그대로 쓴다. 실패 응답에는 평문으로 오는 값이 있다. */
    private String decryptOrRaw(String cipherText) {
        if (cipherText == null || cipherText.isBlank()) {
            return cipherText;
        }
        try {
            String plain = McashCipher.decodeString(cipherText, cryptKey, CHARSET);
            return plain == null ? cipherText : plain.strip();
        } catch (RuntimeException e) {
            return cipherText;
        }
    }

    private static void requireMac(String given, String material, String where) {
        if (given == null || !given.equalsIgnoreCase(sha256Hex(material))) {
            throw new IdentityVerificationException("mcash " + where + " 검증키 불일치 — 위·변조된 응답");
        }
    }

    /** 샘플이 {@code URLEncoder.encode(userInfo, "EUC-KR")} 로 만든다. 이름이 한글이라 문자셋이 중요하다. */
    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new IdentityVerificationException("EUC-KR 을 쓸 수 없습니다", e);
        }
    }

    static String sha256Hex(String plain) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없습니다.", e);
        }
    }
}
