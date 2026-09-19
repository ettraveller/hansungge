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
 * 소셜 로그인은 사용자가 <b>구글이냐 네이버냐를 고른다</b>. 본인확인은 다르다 —
 * 사용자에게 "토스로 하시겠습니까"를 물을 이유가 없고, 통신사·PASS 선택은 공급자 창
 * 안에서 이루어진다. 그래서 서버가 정한다.
 *
 * <h2>무엇이 뜨는가는 설정 한 줄이 정한다</h2>
 * {@code IDENTITY_PROVIDER=MOCK|TOSS|INICIS}. 각 구현체가 그 값일 때만 빈이 되므로
 * 여기에는 보통 0 개나 1 개가 들어온다. 0 개면 가입이 닫힌다 — 기존 회원 로그인은 된다.
 */
@Slf4j
@Component
public class IdentityVerificationRegistry {

    private final List<IdentityVerificationProvider> providers;

    public IdentityVerificationRegistry(List<IdentityVerificationProvider> providers) {
        this.providers = providers;
    }

    @PostConstruct
    void logAvailability() {
        List<String> usable = providers.stream()
                .filter(IdentityVerificationProvider::isConfigured)
                .map(IdentityVerificationProvider::code)
                .toList();

        if (usable.isEmpty()) {
            log.warn("""
                    [IDENTITY] 사용 가능한 본인확인 공급자가 없습니다. 새 회원가입이 닫힙니다.
                    기존 회원의 로그인은 그대로 됩니다. 계약(TODO E-4)이 끝나면 아래를 주입하세요.
                      IDENTITY_PROVIDER=TOSS 또는 INICIS
                      IDENTITY_CLIENT_ID / IDENTITY_CLIENT_SECRET""");
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

    /** 화면이 가입 버튼을 그릴지 판단하는 데 쓴다. */
    public boolean isAvailable() {
        return providers.stream().anyMatch(IdentityVerificationProvider::isConfigured);
    }
}
