package com.hanseongge.domain.user.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.hanseongge.global.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * KG이니시스 통합인증 — 간편인증({@code reqSvcCd=01}, 기본값).
 *
 * <p>규격: https://manual.inicis.com/sa/auth.html, 샘플: {@code SaSample.zip} (JSP).
 *
 * <h2>왜 간편인증인가 (2026-09-21)</h2>
 * PASS 로 가입시키려면 간편인증이어야 한다. 이니시스 회신: "네이버, 카카오, PASS, 삼성패스는 본인확인
 * 인증서에 포함되지 않습니다." 간편인증은 DI 대신 CI 를 주고, 우리는 CI 를 해시해 같은 사람을 판정한다.
 * 설정({@code IDENTITY_REQ_SVC_CD=03})으로 본인확인으로 돌릴 수도 있다 — 둘 다 CI 를 준다.
 *
 * <h2>흐름</h2>
 * <ol>
 *   <li><b>인증요청</b> — 브라우저가 {@code https://sa.inicis.com/auth}(03 이면 {@code /id/auth})로 폼 POST.
 *       상점아이디·거래번호({@code mTxId})·{@code authHash = SHA256(mid + mTxId + apiKey)}
 *   <li><b>인증응답</b> — 이니시스가 {@code successUrl}(우리 백엔드)로 {@code resultCode}·{@code txId}·
 *       {@code authRequestUrl}·{@code token} 을 보낸다
 *   <li><b>결과조회</b> — 우리 서버가 {@code authRequestUrl} 로 {@code {mid, txId}} 를 POST (서버 간 HTTPS)
 *   <li><b>결과조회응답</b> — 이름·전화·생년월일·CI 가 SEED 로 암호화돼 온다.
 *       키는 2단계의 {@code token} ({@link InicisSeedCipher})
 * </ol>
 *
 * <h2>지키는 것 (규격의 "필수 검증")</h2>
 * <ul>
 *   <li>{@code authRequestUrl} 이 이니시스 주소({@code kssa}·{@code fcsa}.inicis.com)인지 확인한다.
 *       이 값은 브라우저를 거쳐 오므로, 확인하지 않으면 <b>우리 서버가 공격자가 고른 주소를
 *       호출</b>하게 된다(SSRF)
 *   <li>결과조회 응답의 {@code mTxId} 가 우리가 발급한 거래번호와 같은지 확인한다 —
 *       다른 사람의 인증 결과를 내 가입에 붙이는 경로를 막는다
 *   <li>CI 원본은 결과 객체에 담아 넘길 뿐 로그에 남기지 않는다. 판정 키로 바꾸는 곳은 서비스 계층이다
 *   <li>DI·성별·내외국인은 읽지 않는다 — 간편인증은 주지 않고, 우리도 쓰지 않는다
 * </ul>
 *
 * <h2>CI 가 안 올 수 있다</h2>
 * 규격 STEP4 {@code userCi}: "카카오 : 제한적 제공". 비어 있으면 그대로 넘기고, 서비스 계층이
 * "다른 인증 수단을 선택해 주세요"로 막는다.
 *
 * <h2>계약 전 테스트</h2>
 * 공식 샘플의 테스트 상점아이디({@code INIiasTest})와 그 apiKey 를 환경변수로 넣으면 실제 이니시스
 * 인증창까지 붙는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.identity.provider", havingValue = InicisIdentityVerificationProvider.CODE)
public class InicisIdentityVerificationProvider implements IdentityVerificationProvider {

    public static final String CODE = "INICIS";

    /** 간편인증·전자서명 */
    private static final String SIMPLE_AUTH_URL = "https://sa.inicis.com/auth";
    /** 본인확인 */
    private static final String IDENTITY_AUTH_URL = "https://sa.inicis.com/id/auth";
    private static final Set<String> SERVICE_CODES = Set.of("01", "03");
    private static final Set<String> LOOKUP_HOSTS = Set.of("kssa.inicis.com", "fcsa.inicis.com");
    private static final DateTimeFormatter BIRTH = DateTimeFormatter.BASIC_ISO_DATE;

    private final AppProperties.Identity config;
    private final String serviceCode;
    private final RestClient restClient;

    public InicisIdentityVerificationProvider(AppProperties appProperties, RestClient.Builder builder) {
        this.config = appProperties.identity();
        this.serviceCode = config.reqSvcCd().trim();
        if (!SERVICE_CODES.contains(serviceCode)) {
            // 02(전자서명)는 신원 확인이 아니다. 잘못 넣으면 조용히 이상한 결과가 나오기보다 뜨지 않는 편이 낫다.
            throw new IllegalStateException("IDENTITY_REQ_SVC_CD 는 01(간편인증) 또는 03(본인확인)이어야 합니다: "
                    + serviceCode);
        }

        // 규격 권고 타임아웃 5초. 없으면 이니시스가 늦을 때 가입 요청 스레드가 묶인다.
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(3))
                .withReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();

        if (!isConfigured()) {
            log.warn("""
                    [IDENTITY] IDENTITY_PROVIDER=INICIS 인데 상점아이디/apiKey 가 비어 있습니다. 자체 가입이 닫힙니다.
                    IDENTITY_MID / IDENTITY_API_KEY 를 주입하세요.""");
        }
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean isConfigured() {
        return !config.mid().isBlank() && !config.apiKey().isBlank();
    }

    @Override
    public IdentityStartForm start(String requestId, String callbackUrl, IdentityMethod method) {
        String mid = config.mid();
        // 설정값은 "기본 방식" 일 뿐이다. 실제로 어느 창을 띄울지는 쓰는 사람이 고른다 —
        // 간편인증 앱이 없는 분은 문자로 받아야 가입을 마칠 수 있다.
        String svcCd = method.code();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("mid", mid);
        fields.put("reqSvcCd", svcCd);
        fields.put("mTxId", requestId);
        fields.put("successUrl", callbackUrl);
        fields.put("failUrl", callbackUrl);
        fields.put("authHash", sha256Hex(mid + requestId + config.apiKey()));
        // 특정 사용자 고정을 쓰지 않는다 — 가입 전이라 우리가 아는 신원이 없다.
        fields.put("flgFixedUser", "N");
        // 결과조회 응답의 개인정보를 SEED 로 암호화해 받는다. 규격상 필수 고정값.
        fields.put("reservedMsg", "isUseToken=Y");
        return new IdentityStartForm(authUrl(svcCd), "POST", fields);
    }

    /** 요청 주소는 서비스마다 다르다 (규격 "통합인증 요청URL"). */
    static String authUrl(String serviceCode) {
        return "03".equals(serviceCode) ? IDENTITY_AUTH_URL : SIMPLE_AUTH_URL;
    }

    @Override
    public IdentityVerificationResult verify(String requestId, Map<String, String> returned) {
        String resultCode = returned.get("resultCode");
        if (!"0000".equals(resultCode)) {
            // 사용자가 창을 닫았거나 인증에 실패했다. resultMsg 는 로그에만 남긴다.
            throw new IdentityVerificationException(
                    "이니시스 인증 실패 resultCode=" + resultCode + " msg=" + returned.get("resultMsg"));
        }

        String txId = require(returned, "txId");
        String token = require(returned, "token");
        URI lookupUrl = requireInicisUrl(require(returned, "authRequestUrl"));

        JsonNode body = lookup(lookupUrl, txId);

        String lookupCode = text(body, "resultCode");
        if (!"0000".equals(lookupCode)) {
            throw new IdentityVerificationException(
                    "이니시스 결과조회 실패 resultCode=" + lookupCode + " msg=" + text(body, "resultMsg"));
        }
        // 이 결과가 우리가 연 인증창의 것인가. 브라우저가 가져온 txId 는 남의 것일 수 있다.
        if (!requestId.equals(text(body, "mTxId"))) {
            throw new IdentityVerificationException("이니시스 거래번호 불일치 — 다른 요청의 인증 결과");
        }
        // 어느 방식으로 왔는지는 묻지 않는다. 결과가 <b>이 요청의 것인지</b>는 바로 위에서
        // 거래번호(txId == mTxId)로 이미 확인했고, 그 값은 우리가 만들어 서명된 토큰에만
        // 넣어 둔 것이라 남이 맞출 수 없다. 간편인증이든 문자든 둘 다 CI 를 준다.
        String svcCd = text(body, "svcCd");
        if (svcCd != null && !SERVICE_CODES.contains(svcCd)) {
            throw new IdentityVerificationException("알 수 없는 인증 서비스 코드 svcCd=" + svcCd);
        }

        String iv = config.seedIv();
        String name = requireValue(InicisSeedCipher.decrypt(text(body, "userName"), token, iv), "userName");
        LocalDate birth = parseBirth(InicisSeedCipher.decrypt(text(body, "userBirthday"), token, iv));
        String phone = requireValue(InicisSeedCipher.decrypt(text(body, "userPhone"), token, iv), "userPhone");
        // 없을 수 있다(카카오 "제한적 제공"). 여기서 막지 않고 넘긴다 — 판정은 서비스 계층이 한다.
        String ci = InicisSeedCipher.decrypt(text(body, "userCi"), token, iv);
        // DI 는 본인확인(reqSvcCd=03)에서 온다. 간편인증(01)은 주지 않으므로 보통 비어 있다.
        //
        // ⚠️ 필드 이름을 규격서로 확인하지 못했다 — 03 은 아직 계약 전이라 실제 응답을 본 적이
        //    없다. userCi 와 짝이 되는 userDi 로 읽되, 아래 로그가 "없음" 으로만 남으면
        //    규격서에서 이름을 확인할 것 (TODO E-8).
        String di = InicisSeedCipher.decrypt(text(body, "userDi"), token, iv);

        // providerDevCd 는 인증 수단(PASS·토스 등)이다. 로그로 남겨 두면 "CI 가 안 온 수단"을 가려낼 수 있다.
        log.info("[IDENTITY] 이니시스 인증 통과. svcCd={} txId={} provider={} ci={} di={}",
                serviceCode, txId, text(body, "providerDevCd"),
                ci == null || ci.isBlank() ? "없음" : "있음",
                di == null || di.isBlank() ? "없음" : "있음");
        // 통신사는 주지 않는다. providerDevCd 는 인증 수단이지 통신사가 아니라 넣지 않는다.
        return new IdentityVerificationResult(txId, ci, di, name, birth, phone, null);
    }

    private JsonNode lookup(URI lookupUrl, String txId) {
        try {
            JsonNode body = restClient.post()
                    .uri(lookupUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("mid", config.mid(), "txId", txId))
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new IdentityVerificationException("이니시스 결과조회 응답이 비어 있습니다");
            }
            return body;
        } catch (RestClientException e) {
            throw new IdentityVerificationException("이니시스 결과조회 통신 실패", e);
        }
    }

    /**
     * 결과조회 주소가 이니시스 것인지 확인한다 (규격 STEP2·3 "필수 검증").
     *
     * <p>문자열 앞부분 비교({@code startsWith})로 하지 않는다 — {@code https://kssa.inicis.com.evil.io}
     * 나 {@code https://kssa.inicis.com@evil.io} 가 통과한다. 주소를 해석해 호스트를 정확히 비교한다.
     */
    static URI requireInicisUrl(String raw) {
        try {
            URI uri = new URI(raw);
            boolean ok = "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getUserInfo() == null
                    && uri.getHost() != null
                    && LOOKUP_HOSTS.contains(uri.getHost().toLowerCase())
                    && (uri.getPort() == -1 || uri.getPort() == 443);
            if (!ok) {
                throw new IdentityVerificationException("이니시스 주소가 아닌 결과조회 URL: " + raw);
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IdentityVerificationException("결과조회 URL 형식 오류", e);
        }
    }

    private static LocalDate parseBirth(String value) {
        try {
            return LocalDate.parse(requireValue(value, "userBirthday"), BIRTH);
        } catch (DateTimeParseException e) {
            throw new IdentityVerificationException("생년월일 형식 오류", e);
        }
    }

    private static String require(Map<String, String> map, String key) {
        return requireValue(map.get(key), key);
    }

    private static String requireValue(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IdentityVerificationException("이니시스 응답에 " + key + " 가 없습니다");
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
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
