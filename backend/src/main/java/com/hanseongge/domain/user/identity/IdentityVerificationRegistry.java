package com.hanseongge.domain.user.identity;

import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 쓸 수 있는 본인확인 공급자를 고른다.
 *
 * <h2>왜 하나만 고르나</h2>
 * 소셜 로그인은 사용자가 <b>구글이냐 카카오냐를 고른다</b>. 본인확인은 다르다 —
 * 사용자에게 "이니시스로 하시겠습니까"를 물을 이유가 없고, 통신사 선택은 공급자 창
 * 안에서 이루어진다. 그래서 등록된 것 중 하나를 서버가 정한다.
 *
 * <p>둘 이상 켜져 있으면 목이 우선순위에서 밀린다 — 실물이 있는데 목으로 도는 것이
 * 가장 나쁜 결과다. 다만 {@code prod} 에는 목이 애초에 등록되지 않는다.
 */
@Slf4j
@Component
public class IdentityVerificationRegistry {

    private final List<IdentityVerificationProvider> providers;

    public IdentityVerificationRegistry(List<IdentityVerificationProvider> providers) {
        // 실물을 앞에 둔다. 목은 이름으로 뒤로 밀어낸다.
        this.providers = providers.stream()
                .sorted((a, b) -> Boolean.compare(
                        MockIdentityVerificationProvider.CODE.equals(a.code()),
                        MockIdentityVerificationProvider.CODE.equals(b.code())))
                .toList();
    }

    @PostConstruct
    void logAvailability() {
        List<String> usable = providers.stream()
                .filter(IdentityVerificationProvider::isConfigured)
                .map(IdentityVerificationProvider::code)
                .toList();

        if (usable.isEmpty()) {
            log.warn("""
                    [IDENTITY] 사용 가능한 본인확인 공급자가 없습니다. 회원가입이 진행되지 않습니다.
                    계약(TODO E-4)이 끝나면 아래를 주입하세요.
                      app.identity.inicis.enabled=true
                      APP_IDENTITY_INICIS_MID / APP_IDENTITY_INICIS_API_KEY""");
            return;
        }
        log.info("[IDENTITY] 사용 가능한 본인확인 공급자: {}", usable);
    }

    /**
     * 쓸 공급자.
     *
     * @throws BusinessException 하나도 없으면 503. 404 가 아닌 이유는 <b>없는 것이 아니라
     *                           아직 준비되지 않은 것</b>이기 때문이다
     */
    public IdentityVerificationProvider require() {
        return providers.stream()
                .filter(IdentityVerificationProvider::isConfigured)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE,
                        "본인확인 공급자 설정 없음"));
    }

    /** 화면이 "본인확인 준비 중" 안내를 띄울지 판단하는 데 쓴다. */
    public boolean isAvailable() {
        return providers.stream().anyMatch(IdentityVerificationProvider::isConfigured);
    }
}
