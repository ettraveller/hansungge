package com.hanseongge.domain.user.identity;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * KG이니시스 통합인증 — <b>아직 구현하지 않은 껍데기다.</b>
 *
 * <h2>왜 비어 있는가 (TODO E-4)</h2>
 * 상점 아이디와 인증키는 <b>계약이 끝나야</b> 나온다. 그 전에 동작하는 코드를 넣으면
 * 검증할 방법이 없는 코드가 쌓이고, 나중에 실제 응답 형식이 다르면 통째로 다시 쓰게 된다.
 * 규격을 손에 넣은 뒤 한 번에 쓴다.
 *
 * <h2>그래서 기본 비활성이다</h2>
 * {@code @ConditionalOnProperty} 로 꺼 두고, 켜졌는데 미구현이면 기동 시 경고한다.
 * 호출되면 <b>조용히 실패하지 않고</b> 사유를 담아 예외를 던진다 — empty 나 null 을
 * 돌려주면 "본인확인이 되는데 값이 없는 상태"로 오인되어 아무도 미구현을 눈치채지 못한다.
 *
 * <p>개발은 {@link MockIdentityVerificationProvider} 로 진행한다.
 *
 * <h2>구현할 때 지킬 것</h2>
 * <ul>
 *   <li>브라우저가 들고 온 값을 믿지 않는다. 공급자 서버에 되물어 확인한 값만 쓴다
 *   <li><b>CI 를 받지 않는다.</b> 응답에 있어도 {@link IdentityVerificationResult} 에 자리가 없다
 *   <li>{@code requestId} 대조 — 다른 요청의 결과를 이 요청에 붙이는 경로를 막는다
 *   <li>실패 원문을 예외 메시지에 담지 않는다. 상점 아이디가 섞여 나온다
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.identity.inicis.enabled", havingValue = "true")
public class InicisIdentityVerificationProvider implements IdentityVerificationProvider {

    public static final String CODE = "INICIS";

    @PostConstruct
    void warnNotImplemented() {
        log.warn("""
                [IDENTITY] InicisIdentityVerificationProvider 가 활성화되었으나 아직 구현되지 않았습니다.
                본인확인 요청은 전부 실패하고, 회원가입이 진행되지 않습니다.
                계약 완료(TODO E-4) 전까지는 app.identity.inicis.enabled 를 켜지 마세요.""");
    }

    @Override
    public String code() {
        return CODE;
    }

    /**
     * 켜져 있으면 "설정됐다"고 답한다.
     *
     * <p>미구현 사실은 {@link #startUrl}·{@link #verify} 의 예외로 드러난다. 여기서
     * false 를 돌려주면 503("준비되지 않음")이 되어 <b>설정 누락과 구별되지 않는다.</b>
     */
    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public String startUrl(String requestId, String returnUrl) {
        throw new IdentityVerificationException(
                "KG이니시스 통합인증 미구현 (TODO E-4: 계약·상점 아이디 발급 대기)");
    }

    @Override
    public IdentityVerificationResult verify(String requestId, String providerToken) {
        throw new IdentityVerificationException(
                "KG이니시스 통합인증 미구현 (TODO E-4: 계약·상점 아이디 발급 대기)");
    }
}
