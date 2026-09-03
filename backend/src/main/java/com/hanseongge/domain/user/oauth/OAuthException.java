package com.hanseongge.domain.user.oauth;

/**
 * 소셜 인증 연동 실패.
 *
 * <p>서비스 계층이 이 예외를 {@code OAUTH_EXCHANGE_FAILED} 로 바꿔 내보낸다. 제공자가 준
 * 오류 원문을 사용자 응답에 그대로 싣지 않기 위한 경계다 — 원문에는 클라이언트 ID 나
 * 내부 파라미터가 섞여 나오는 경우가 있다. 상세는 로그에만 남는다.
 */
public class OAuthException extends RuntimeException {

    public OAuthException(String message) {
        super(message);
    }

    public OAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
