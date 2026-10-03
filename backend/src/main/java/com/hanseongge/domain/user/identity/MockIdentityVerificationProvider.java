package com.hanseongge.domain.user.identity;

import com.hanseongge.global.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * 개발용 본인인증 — 실제로 아무것도 확인하지 않는다.
 *
 * <h2>왜 필요한가</h2>
 * 본인확인은 계약이 끝나야 붙는데(TODO E-4), 가입 흐름 전체는 그 전에 만들고 검증해야
 * 한다. 이것이 없으면 로컬에서 회원가입을 <b>한 번도 끝까지 해볼 수 없다.</b>
 *
 * <h2>운영에서는 기동이 멈춘다</h2>
 * {@code IDENTITY_PROVIDER=MOCK} 일 때만 빈이 되고, 그 상태로 {@code prod} 프로파일이
 * 켜져 있으면 <b>생성자에서 예외를 던져 애플리케이션이 뜨지 않는다.</b> 설정 실수로 목이
 * 운영에 뜨면 "아무 이름으로나 가입되는" 사이트가 되고, 그 사실을 한참 뒤에 알게 된다.
 * 조용히 도는 것보다 배포가 실패하는 편이 낫다.
 *
 * <h2>같은 사람을 돌려준다</h2>
 * {@code app.identity.mock-persona}(기본 1) 가 같으면 <b>같은 CI·같은 휴대폰</b>이다.
 * 그래야 "이미 가입한 사람이 다시 가입하면 기존 계정으로 안내" "소셜 회원의 본인인증"을
 * 로컬에서 재현할 수 있다. 다른 사람으로 가입해 보려면 값을 바꿔 재기동한다
 * ({@code IDENTITY_MOCK_PERSONA=2}).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.identity.provider", havingValue = MockIdentityVerificationProvider.CODE)
public class MockIdentityVerificationProvider implements IdentityVerificationProvider {

    public static final String CODE = "MOCK";

    private final int persona;

    public MockIdentityVerificationProvider(AppProperties appProperties, Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("""
                    운영(prod)에서 개발용 목 본인확인(IDENTITY_PROVIDER=MOCK)을 켤 수 없습니다.
                    목은 아무것도 확인하지 않아 누구나 아무 이름으로 가입할 수 있게 됩니다.
                    계약 전이면 IDENTITY_PROVIDER 를 비워 두세요 — 자체 가입만 닫히고 소셜 가입·로그인은 됩니다.""");
        }
        this.persona = appProperties.identity().mockPersona();
        log.warn("""
                [IDENTITY] 개발용 목 본인확인이 켜져 있습니다. 실제로 아무것도 확인하지 않습니다.
                인증창을 띄우는 대신 returnUrl 로 즉시 돌아옵니다. 돌려주는 사람: persona={} phone={}""",
                persona, phone());
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    /**
     * 인증창 대신 <b>백엔드 수신 주소로 곧바로 보내는 폼</b>을 준다.
     *
     * <p>이니시스가 인증을 마치고 보내는 것과 같은 모양(결과코드·거래ID)이라, 수신 주소 →
     * 화면 → 결과 제출까지 실물과 <b>같은 경로</b>를 탄다. 화면 코드에 "개발일 때는 이렇게"
     * 같은 분기를 넣으면 그 분기 자체가 검증되지 않는다.
     */
    @Override
    public IdentityStartForm start(String requestId, String callbackUrl, IdentityMethod method) {
        return new IdentityStartForm(callbackUrl, "POST", Map.of(
                "resultCode", "0000",
                "txId", "mock-" + UUID.randomUUID(),
                "mTxId", requestId));
    }

    @Override
    public IdentityVerificationResult verify(String requestId, Map<String, String> returned) {
        String txId = returned.get("txId");
        if (!"0000".equals(returned.get("resultCode")) || txId == null || !txId.startsWith("mock-")) {
            throw new IdentityVerificationException("목 본인확인 결과가 아닙니다");
        }
        // 실물의 "되물은 결과의 거래번호가 우리 요청과 같은가" 대조를 흉내 낸다.
        if (!requestId.equals(returned.get("mTxId"))) {
            throw new IdentityVerificationException("목 본인확인 거래번호 불일치");
        }

        log.info("[IDENTITY] 목 본인인증 통과. persona={} phone={}", persona, phone());
        // 거래번호는 매번 새로 만든다 — 같은 거래번호 재사용은 ux_iv_tx 가 막는다.
        // CI 는 persona 마다 고정 — 같은 persona 는 같은 사람이다. 실제 CI 모양(88자 Base64)은 흉내 내지 않는다.
        return new IdentityVerificationResult(
                "mock-tx-" + UUID.randomUUID(),
                "MOCK-CI-PERSONA-" + persona,
                "테스트회원" + persona,
                LocalDate.of(1990, 1, 1),
                phone(),
                "MOCK");
    }

    /**
     * 실존하지 않는 대역(010-0000-xxxx). 실수로 문자를 보내도 아무에게도 가지 않는다.
     */
    private String phone() {
        return "0100000" + String.format("%04d", persona % 10_000);
    }
}
