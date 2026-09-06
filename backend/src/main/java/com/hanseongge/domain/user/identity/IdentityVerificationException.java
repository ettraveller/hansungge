package com.hanseongge.domain.user.identity;

/**
 * 본인확인 연동 실패.
 *
 * <p>{@code OAuthException} 과 같은 경계다. 서비스 계층이 이 예외를
 * {@code IDENTITY_VERIFICATION_FAILED} 로 바꿔 내보낸다 — 공급자가 준 오류 원문에는
 * 상점 아이디나 내부 파라미터가 섞여 나오는 경우가 있어 사용자 응답에 싣지 않는다.
 * 상세는 로그에만 남는다.
 */
public class IdentityVerificationException extends RuntimeException {

    public IdentityVerificationException(String message) {
        super(message);
    }

    public IdentityVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
