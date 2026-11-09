package com.hanseongge.domain.user.identity;

import com.hanseongge.global.config.AppProperties;
import mup.mcash.module.common.McashCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KG파이낸셜(mcash) 휴대폰본인확인.
 *
 * <p>여기서 고정하는 것은 두 가지다 — <b>SEED 왕복이 공급자 모듈과 맞는가</b>, 그리고
 * <b>위·변조된 응답을 받아들이지 않는가</b>. 둘 다 틀리면 남의 신원으로 가입이 된다.
 *
 * <p>공급자 쪽 값은 {@link McashCipher} 로 직접 만든다. 우리 코드가 쓰는 것과 같은 모듈이라
 * "둘 다 똑같이 틀린" 경우를 잡지 못하지만, 그 모듈이 규격이다 — 우리가 재구현한 것이 아니다.
 */
class McashIdentityVerificationProviderTest {

    private static final String SVC_ID = "190000000001";
    private static final String KEY = "TESTkey1";
    private static final String CRYPT_KEY = "TESTkey1" + "19000000";
    private static final String NOTI_URL = "https://api.hanseongge.com/api/v1/auth/identity/noti";
    private static final String OK_URL = "https://api.hanseongge.com/api/v1/auth/identity/return";

    private static final String TRADE_ID = "hsg20261109abcd1234";
    private static final String MOBIL_ID = "M20261109000001";
    private static final String SIGN_DATE = "20261109113000";
    private static final String CI = "QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVph";
    private static final String DI = "ZGlfdmFsdWVfMDAx";

    private McashIdentityVerificationProvider provider;

    @BeforeEach
    void setUp() {
        provider = newProvider(Map.of(
                "app.identity.provider", "MCASH",
                "app.identity.mid", SVC_ID,
                "app.identity.api-key", KEY,
                "app.identity.noti-url", NOTI_URL,
                "app.identity.site-url", "hanseongge.com"));
    }

    private static McashIdentityVerificationProvider newProvider(Map<String, Object> settings) {
        AppProperties properties = new Binder(new MapConfigurationPropertySource(settings))
                .bind("app", AppProperties.class)
                .orElseThrow(IllegalStateException::new);
        return new McashIdentityVerificationProvider(properties, new McashNotiStore(properties));
    }

    @Nested
    @DisplayName("설정")
    class Configuration {

        @Test
        @DisplayName("서비스ID·암호화키·노티주소가 다 있어야 쓸 수 있다")
        void needsAllThree() {
            assertThat(provider.isConfigured()).isTrue();
        }

        @Test
        @DisplayName("노티 주소가 없으면 꺼진다 — 그 주소가 없으면 신원이 올 길이 없다")
        void withoutNotiUrl() {
            assertThat(newProvider(Map.of(
                    "app.identity.provider", "MCASH",
                    "app.identity.mid", SVC_ID,
                    "app.identity.api-key", KEY)).isConfigured()).isFalse();
        }

        @Test
        @DisplayName("암호화키가 8자리에 못 미치면 꺼진다 — 잘린 키로 돌면 복호화가 조용히 깨진다")
        void withShortKey() {
            assertThat(newProvider(Map.of(
                    "app.identity.provider", "MCASH",
                    "app.identity.mid", SVC_ID,
                    "app.identity.api-key", "short",
                    "app.identity.noti-url", NOTI_URL)).isConfigured()).isFalse();
        }
    }

    @Nested
    @DisplayName("인증 요청")
    class Start {

        @Test
        @DisplayName("실거래(PAY_MODE=10)는 운영 주소로 간다")
        void liveUrl() {
            assertThat(start().action()).isEqualTo("https://auth.mobilians.co.kr/goAuthMain.mcash");
        }

        @Test
        @DisplayName("PAY_MODE=00 이면 테스트 주소로 간다")
        void testUrl() {
            McashIdentityVerificationProvider test = newProvider(Map.of(
                    "app.identity.provider", "MCASH",
                    "app.identity.mid", SVC_ID,
                    "app.identity.api-key", KEY,
                    "app.identity.noti-url", NOTI_URL,
                    "app.identity.pay-mode", "00"));
            assertThat(test.start(TRADE_ID, OK_URL, IdentityMethod.PHONE).action())
                    .isEqualTo("https://authtest.mobilians.co.kr/goAuthMain.mcash");
        }

        @Test
        @DisplayName("고정값 — CASH_GB=CI, Cryptyn=Y, LOGO_YN=N, CI_Mode=61")
        void fixedFields() {
            assertThat(start().fields())
                    .containsEntry("CASH_GB", "CI")
                    .containsEntry("Cryptyn", "Y")
                    .containsEntry("LOGO_YN", "N")
                    .containsEntry("CI_Mode", "61")
                    .containsEntry("CI_SVCID", SVC_ID)
                    .containsEntry("Tradeid", TRADE_ID);
        }

        @Test
        @DisplayName("Okurl·Notiurl 은 암호화해 보낸다. 공급자가 풀면 원래 주소가 나온다")
        void urlsAreEncrypted() {
            Map<String, String> fields = start().fields();
            assertThat(fields.get("Okurl")).isNotEqualTo(OK_URL).matches("[0-9a-f]+");
            assertThat(decrypt(fields.get("Okurl"))).isEqualTo(OK_URL);
            assertThat(decrypt(fields.get("Notiurl"))).isEqualTo(NOTI_URL);
            assertThat(decrypt(fields.get("Cryptstring"))).isEqualTo(TRADE_ID + OK_URL);
        }

        @Test
        @DisplayName("한글이 섞이지 않는다 — 폼이 EUC-KR 로 전송되므로 전부 ASCII 로 보낸다")
        void asciiOnly() {
            start().fields().values().forEach(value ->
                    assertThat(value).matches("\\p{ASCII}*"));
        }

        @Test
        @DisplayName("암호화키를 폼에 싣지 않는다")
        void doesNotLeakKey() {
            assertThat(start().fields().values()).noneMatch(value -> value.contains(KEY));
        }

        private IdentityStartForm start() {
            return provider.start(TRADE_ID, OK_URL, IdentityMethod.PHONE);
        }
    }

    @Nested
    @DisplayName("노티 수신")
    class Noti {

        @Test
        @DisplayName("Mac 이 맞으면 받아들인다")
        void accepts() {
            assertThat(provider.acceptNoti(noti(new HashMap<>()))).isTrue();
        }

        @Test
        @DisplayName("Mac 이 틀리면 거부한다 — 아무나 부를 수 있는 주소다")
        void rejectsForgedMac() {
            Map<String, String> params = noti(new HashMap<>());
            params.put("Mac", "0".repeat(64));
            assertThat(provider.acceptNoti(params)).isFalse();
        }

        @Test
        @DisplayName("이름만 바꿔치기해도 거부한다 — Mac 재료에 신원이 들어 있다")
        void rejectsTamperedName() {
            Map<String, String> params = noti(new HashMap<>());
            params.put("Name", encrypt("박영희"));
            assertThat(provider.acceptNoti(params)).isFalse();
        }

        @Test
        @DisplayName("실패 노티도 정상 수신이다 — SUCCESS 를 돌려줘야 사용자가 Okurl 로 온다")
        void failureNotiIsStillReceived() {
            Map<String, String> params = noti(Map.of("Resultcd", "9996"));
            assertThat(provider.acceptNoti(params)).isTrue();
            // 다만 신원은 담기지 않는다.
            assertThatThrownBy(() -> provider.verify(TRADE_ID, okurl(Map.of("Resultcd", "9996"))))
                    .isInstanceOf(IdentityVerificationException.class);
        }
    }

    @Nested
    @DisplayName("화면 전환 검증")
    class Verify {

        @Test
        @DisplayName("노티로 받은 신원을 거래번호로 찾아 돌려준다")
        void returnsIdentityFromNoti() {
            provider.acceptNoti(noti(new HashMap<>()));

            IdentityVerificationResult result = provider.verify(TRADE_ID, okurl(Map.of()));

            assertThat(result.name()).isEqualTo("홍길동");
            assertThat(result.phone()).isEqualTo("01012345678");
            assertThat(result.birthDate()).isEqualTo(LocalDate.of(1980, 1, 1));
            assertThat(result.carrier()).isEqualTo("SKT");
            assertThat(result.ci()).isEqualTo(CI);
            assertThat(result.di()).isEqualTo(DI);
            assertThat(result.providerTxId()).isEqualTo(MOBIL_ID);
        }

        @Test
        @DisplayName("남의 거래번호면 거부한다 — 다른 사람의 인증을 내 가입에 붙이는 경로")
        void rejectsOtherTrade() {
            provider.acceptNoti(noti(new HashMap<>()));

            assertThatThrownBy(() -> provider.verify("hsg-다른요청", okurl(Map.of())))
                    .isInstanceOf(IdentityVerificationException.class)
                    .hasMessageContaining("거래번호 불일치");
        }

        @Test
        @DisplayName("Okurl 의 Mac 이 틀리면 거부한다 — 브라우저를 거쳐 온 값이다")
        void rejectsForgedOkurl() {
            provider.acceptNoti(noti(new HashMap<>()));
            Map<String, String> returned = okurl(Map.of());
            returned.put("Mac", "f".repeat(64));

            assertThatThrownBy(() -> provider.verify(TRADE_ID, returned))
                    .isInstanceOf(IdentityVerificationException.class)
                    .hasMessageContaining("위·변조");
        }

        @Test
        @DisplayName("결과코드가 0000 이 아니면 거부한다")
        void rejectsFailure() {
            assertThatThrownBy(() -> provider.verify(TRADE_ID, okurl(Map.of("Resultcd", "0013"))))
                    .isInstanceOf(IdentityVerificationException.class)
                    .hasMessageContaining("0013");
        }

        @Test
        @DisplayName("노티가 오지 않았으면 거부한다 — Okurl 만으로는 신원을 알 수 없다")
        void rejectsWithoutNoti() {
            assertThatThrownBy(() -> provider.verify(TRADE_ID, okurl(Map.of())))
                    .isInstanceOf(IdentityVerificationException.class)
                    .hasMessageContaining("노티 미수신");
        }

        @Test
        @DisplayName("한 번 쓴 노티는 다시 쓸 수 없다 — 인증 하나로 계정 둘을 만드는 경로")
        void notiIsSingleUse() {
            provider.acceptNoti(noti(new HashMap<>()));
            provider.verify(TRADE_ID, okurl(Map.of()));

            assertThatThrownBy(() -> provider.verify(TRADE_ID, okurl(Map.of())))
                    .isInstanceOf(IdentityVerificationException.class)
                    .hasMessageContaining("노티 미수신");
        }
    }

    // --- 공급자 흉내 -------------------------------------------------------

    private static String encrypt(String plain) {
        return McashCipher.encodeString(plain, CRYPT_KEY);
    }

    private static String decrypt(String cipherText) {
        return McashCipher.decodeString(cipherText, CRYPT_KEY, "EUC-KR").strip();
    }

    /** mcash 가 Notiurl 로 보내는 모양. {@code overrides} 는 평문 기준으로 덮어쓴다. */
    private static Map<String, String> noti(Map<String, String> overrides) {
        Map<String, String> plain = new LinkedHashMap<>(Map.of(
                "Resultcd", "0000",
                "Resultmsg", "OK",
                "Name", "홍길동",
                "No", "01012345678",
                "Socialno", "19800101",
                "Commid", "SKT",
                "Sex", "M",
                "Foreigner", "N"));
        plain.putAll(overrides);

        String userInfo = plain.get("Name") + plain.get("No") + plain.get("Socialno")
                + plain.get("Commid") + plain.get("Sex") + plain.get("Foreigner");
        String material = urlEncode(userInfo) + TRADE_ID + SIGN_DATE + DI + CI + MOBIL_ID + CRYPT_KEY;

        Map<String, String> params = new HashMap<>();
        plain.forEach((key, value) -> params.put(key, encrypt(value)));
        params.put("Tradeid", encrypt(TRADE_ID));
        params.put("Svcid", encrypt(SVC_ID));
        params.put("Mobilid", encrypt(MOBIL_ID));
        params.put("Signdate", encrypt(SIGN_DATE));
        params.put("Ci", encrypt(CI));
        params.put("Di", encrypt(DI));
        params.put("Mac", McashIdentityVerificationProvider.sha256Hex(material));
        return params;
    }

    /** mcash 가 Okurl 로 보내는 모양. 신원은 들어 있지 않다. */
    private static Map<String, String> okurl(Map<String, String> overrides) {
        String resultCode = overrides.getOrDefault("Resultcd", "0000");
        Map<String, String> params = new HashMap<>();
        params.put("Resultcd", encrypt(resultCode));
        params.put("Resultmsg", encrypt("OK"));
        params.put("Tradeid", encrypt(TRADE_ID));
        params.put("Svcid", encrypt(SVC_ID));
        params.put("Mobilid", encrypt(MOBIL_ID));
        params.put("Signdate", encrypt(SIGN_DATE));
        params.put("Mac", McashIdentityVerificationProvider.sha256Hex(
                TRADE_ID + SVC_ID + MOBIL_ID + SIGN_DATE + CRYPT_KEY));
        return params;
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "EUC-KR");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
