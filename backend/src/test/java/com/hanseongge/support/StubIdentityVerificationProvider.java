package com.hanseongge.support;

import com.hanseongge.domain.user.entity.Gender;
import com.hanseongge.domain.user.entity.VerifiedIdentity;
import com.hanseongge.domain.user.identity.IdentityVerificationException;
import com.hanseongge.domain.user.identity.IdentityVerificationProvider;
import com.hanseongge.domain.user.identity.IdentityVerificationResult;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 테스트용 본인확인 대역.
 *
 * <p>Mockito 목이 아니라 <b>평범한 클래스</b>다. {@code IdentityVerificationRegistry} 가
 * 기동 시점에 생성자에서 목록을 정렬하는데, 목 객체는 그때 스텁이 걸려 있지 않아
 * {@code code()} 가 null 을 돌려준다 — {@code StubOAuthProvider} 가 같은 이유로 같은
 * 방식을 쓴다.
 *
 * <p>DI 를 <b>지정할 수 있다.</b> 중복가입 방지와 계정 병합은 같은 DI 가 두 번 오는
 * 상황을 만들어야 검증되는데, 매번 임의값이면 그 상황을 만들 수 없다.
 */
public class StubIdentityVerificationProvider implements IdentityVerificationProvider {

    public static final String CODE = "STUB";

    /** false 로 두면 "본인확인 준비 안 됨"(503) 자체를 검증할 수 있다. */
    public boolean configured = true;

    /** 다음 호출이 돌려줄 신원. null 이면 매번 새 DI 를 만든다. */
    public VerifiedIdentity nextIdentity;

    /** 설정해 두면 {@link #verify} 가 그 예외를 던진다. */
    public IdentityVerificationException nextFailure;

    public String lastRequestId;
    public String lastReturnUrl;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String startUrl(String requestId, String returnUrl) {
        this.lastRequestId = requestId;
        this.lastReturnUrl = returnUrl;
        return returnUrl + "?stub=1";
    }

    @Override
    public IdentityVerificationResult verify(String requestId, String providerToken) {
        this.lastRequestId = requestId;
        if (nextFailure != null) {
            throw nextFailure;
        }
        VerifiedIdentity identity = nextIdentity != null ? nextIdentity : anonymous();
        return new IdentityVerificationResult("stub-tx-" + UUID.randomUUID(), identity, "STUB");
    }

    /** 이 신원으로 다음 인증이 통과한다. */
    public void willReturn(VerifiedIdentity identity) {
        this.nextIdentity = identity;
    }

    public void willReturn(String di, String name, String phone) {
        this.nextIdentity = new VerifiedIdentity(di, name, LocalDate.of(1990, 1, 1),
                Gender.MALE, false, phone);
    }

    public void reset() {
        this.configured = true;
        this.nextIdentity = null;
        this.nextFailure = null;
        this.lastRequestId = null;
        this.lastReturnUrl = null;
    }

    private static VerifiedIdentity anonymous() {
        return new VerifiedIdentity("STUB-DI-" + UUID.randomUUID(), "테스트 회원",
                LocalDate.of(1990, 1, 1), Gender.MALE, false, "010-0000-0000");
    }
}
