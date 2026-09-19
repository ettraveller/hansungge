package com.hanseongge.domain.user.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.hanseongge.domain.user.entity.Gender;
import com.hanseongge.domain.user.entity.VerifiedIdentity;
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
 * KG이니시스 통합인증 — 본인확인({@code reqSvcCd=03}).
 *
 * <p>규격: https://manual.inicis.com/sa/auth.html, 샘플: {@code SaSample.zip} (JSP).
 *
 * <h2>흐름</h2>
 * <ol>
 *   <li><b>인증요청</b> — 브라우저가 {@code https://sa.inicis.com/id/auth} 로 폼 POST.
 *       상점아이디·거래번호({@code mTxId})·{@code authHash = SHA256(mid + mTxId + apiKey)}
 *   <li><b>인증응답</b> — 이니시스가 {@code successUrl}(우리 백엔드)로 {@code resultCode}·{@code txId}·
 *       {@code authRequestUrl}·{@code token} 을 보낸다
 *   <li><b>결과조회</b> — 우리 서버가 {@code authRequestUrl} 로 {@code {mid, txId}} 를 POST (서버 간 HTTPS)
 *   <li><b>결과조회응답</b> — 이름·전화·생년월일·성별·내외국인·DI 가 SEED 로 암호화돼 온다.
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
 *   <li>CI 는 받지 않는다. 응답에 있어도 읽지 않는다 ({@link IdentityVerificationResult} 에 자리가 없다)
 * </ul>
 *
 * <h2>계약 전 테스트</h2>
 * 공식 샘플의 테스트 상점아이디({@code INIiasTest})와 그 apiKey 를 환경변수로 넣으면 실제 이니시스
 * 인증창까지 붙는다. ⚠️ 샘플은 간편인증({@code 01})으로 되어 있고, <b>DI 는 본인확인({@code 03})
 * 에서만 온다</b>(규격 STEP4). 테스트 MID 가 03 을 허용하지 않으면 DI 가 없어 여기서 실패한다 —
 * 그때는 로그에 사유가 남는다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.identity.provider", havingValue = InicisIdentityVerificationProvider.CODE)
public class InicisIdentityVerificationProvider implements IdentityVerificationProvider {

    public static final String CODE = "INICIS";

    private static final String AUTH_URL = "https://sa.inicis.com/id/auth";
    private static final String SERVICE_IDENTITY = "03";
    private static final Set<String> LOOKUP_HOSTS = Set.of("kssa.inicis.com", "fcsa.inicis.com");
    private static final DateTimeFormatter BIRTH = DateTimeFormatter.BASIC_ISO_DATE;

    private final AppProperties.Identity config;
    private final RestClient restClient;

    public InicisIdentityVerificationProvider(AppProperties appProperties, RestClient.Builder builder) {
        this.config = appProperties.identity();

        // 규격 권고 타임아웃 5초. 없으면 이니시스가 늦을 때 가입 요청 스레드가 묶인다.
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(3))
                .withReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();

        if (!isConfigured()) {
            log.warn("""
                    [IDENTITY] IDENTITY_PROVIDER=INICIS 인데 상점아이디/apiKey 가 비어 있습니다. 가입이 닫힙니다.
                    IDENTITY_CLIENT_ID(MID) / IDENTITY_CLIENT_SECRET(apiKey) 를 주입하세요.""");
        }
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean isConfigured() {
        return !config.clientId().isBlank() && !config.clientSecret().isBlank();
    }

    @Override
    public IdentityStartForm start(String requestId, String callbackUrl) {
        String mid = config.clientId();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("mid", mid);
        fields.put("reqSvcCd", SERVICE_IDENTITY);
        fields.put("mTxId", requestId);
        fields.put("successUrl", callbackUrl);
        fields.put("failUrl", callbackUrl);
        fields.put("authHash", sha256Hex(mid + requestId + config.clientSecret()));
        // 특정 사용자 고정을 쓰지 않는다 — 가입 전이라 우리가 아는 신원이 없다.
        fields.put("flgFixedUser", "N");
        // 결과조회 응답의 개인정보를 SEED 로 암호화해 받는다. 규격상 필수 고정값.
        fields.put("reservedMsg", "isUseToken=Y");
        if (!config.diCode().isBlank()) {
            fields.put("DI_CODE", config.diCode());
        }
        return new IdentityStartForm(AUTH_URL, "POST", fields);
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
        String svcCd = text(body, "svcCd");
        if (svcCd != null && !SERVICE_IDENTITY.equals(svcCd)) {
            throw new IdentityVerificationException("본인확인(03)이 아닌 인증 결과 svcCd=" + svcCd);
        }

        String di = InicisSeedCipher.decrypt(text(body, "userDi"), token);
        if (di == null || di.isBlank()) {
            // 간편인증으로 처리됐거나 DI 가 계약에 포함되지 않았다. 같은 사람 판정의 근거가 없다.
            throw new IdentityVerificationException("이니시스 응답에 DI 가 없습니다 (본인확인 계약·DI_CODE 확인)");
        }

        VerifiedIdentity identity = new VerifiedIdentity(
                di,
                requireValue(InicisSeedCipher.decrypt(text(body, "userName"), token), "userName"),
                parseBirth(InicisSeedCipher.decrypt(text(body, "userBirthday"), token)),
                parseGender(InicisSeedCipher.decrypt(text(body, "userGender"), token)),
                "1".equals(InicisSeedCipher.decrypt(text(body, "isForeign"), token)),
                requireValue(InicisSeedCipher.decrypt(text(body, "userPhone"), token), "userPhone"));

        log.info("[IDENTITY] 이니시스 본인확인 통과. txId={} provider={}", txId, text(body, "providerDevCd"));
        // 통신사는 주지 않는다. providerDevCd 는 인증 수단(PASS·토스 등)이지 통신사가 아니라 넣지 않는다.
        return new IdentityVerificationResult(txId, identity, null);
    }

    private JsonNode lookup(URI lookupUrl, String txId) {
        try {
            JsonNode body = restClient.post()
                    .uri(lookupUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("mid", config.clientId(), "txId", txId))
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

    private static Gender parseGender(String value) {
        return switch (requireValue(value, "userGender")) {
            case "M" -> Gender.MALE;
            case "F" -> Gender.FEMALE;
            default -> throw new IdentityVerificationException("알 수 없는 성별 값");
        };
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
