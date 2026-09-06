package com.hanseongge.domain.user.identity;

import com.hanseongge.domain.user.entity.Gender;
import com.hanseongge.domain.user.entity.VerifiedIdentity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 개발용 본인확인 — 실제로 아무것도 확인하지 않는다.
 *
 * <h2>왜 필요한가</h2>
 * 본인확인은 계약이 끝나야 붙는데(TODO E-4), 가입 흐름 전체는 그 전에 만들고 검증해야
 * 한다. 이것이 없으면 로컬에서 회원가입을 <b>한 번도 끝까지 해볼 수 없다.</b>
 *
 * <h2>운영에 뜰 수 없다</h2>
 * {@code @Profile({"local","dev"})} 라 {@code prod} 에서는 빈이 아예 만들어지지 않는다.
 * 설정 실수로 목이 운영에 뜨는 경로가 존재하지 않는다 — 그런 사고는 "가입은 되는데
 * 아무도 본인확인을 하지 않은 회원"을 만들고, 그 사실을 한참 뒤에 알게 된다.
 *
 * <h2>DI 를 요청마다 새로 만든다</h2>
 * 고정값을 쓰면 두 번째 가입이 중복가입으로 막혀 개발 중에 계정을 하나밖에 못 만든다.
 * 대신 <b>중복가입 방지를 로컬에서 확인할 수 없다</b> — 그건 통합테스트의 몫이고,
 * 테스트는 DI 를 직접 정할 수 있는 별도 스텁을 쓴다.
 */
@Slf4j
@Component
@Profile({"local", "dev"})
public class MockIdentityVerificationProvider implements IdentityVerificationProvider {

    public static final String CODE = "MOCK";

    /** 실존하지 않는 번호 대역(010-0000-xxxx)을 쓴다. 실수로 문자를 보내도 아무에게도 안 간다. */
    private static final String PHONE_PREFIX = "010-0000-";

    @PostConstruct
    void warnMockInUse() {
        log.warn("""
                [IDENTITY] 개발용 목 본인확인이 켜져 있습니다. 실제로 아무것도 확인하지 않습니다.
                인증창을 띄우는 대신 returnUrl 로 즉시 돌아옵니다.""");
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
     * 인증창 대신 <b>곧바로 돌아오는 주소</b>를 준다.
     *
     * <p>프론트엔드는 실물과 똑같이 이 주소로 이동하면 되고, 흐름을 분기할 필요가 없다.
     * 화면 코드에 "개발일 때는 이렇게" 같은 분기를 넣으면 그 분기 자체가 검증되지 않는다.
     */
    @Override
    public String startUrl(String requestId, String returnUrl) {
        return UriComponentsBuilder.fromUriString(returnUrl)
                .queryParam("requestId", requestId)
                .queryParam("providerToken", "mock-" + UUID.randomUUID())
                .build(true)
                .toUriString();
    }

    @Override
    public IdentityVerificationResult verify(String requestId, String providerToken) {
        if (providerToken == null || !providerToken.startsWith("mock-")) {
            throw new IdentityVerificationException("목 본인확인 토큰이 아닙니다: " + providerToken);
        }

        String suffix = String.format("%04d", Math.abs(providerToken.hashCode()) % 10_000);
        VerifiedIdentity identity = new VerifiedIdentity(
                "MOCK-DI-" + UUID.randomUUID(),
                "테스트 회원",
                LocalDate.of(1990, 1, 1),
                Gender.MALE,
                false,
                PHONE_PREFIX + suffix);

        log.info("[IDENTITY] 목 본인확인 통과. requestId={} phone={}", requestId, identity.phone());
        return new IdentityVerificationResult("mock-tx-" + UUID.randomUUID(), identity, "MOCK");
    }
}
