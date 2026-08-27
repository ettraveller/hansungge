package com.hanseongge.domain.user.oauth;

import com.hanseongge.domain.user.entity.OAuthProviderType;

/**
 * 소셜 인증 제공자 (설계서 원칙 P5 — 외부 연동은 인터페이스 뒤에 둔다).
 *
 * <p>구글·네이버·카카오는 같은 OAuth2 Authorization Code 흐름을 쓰지만 세부가 전부 다르다.
 * 네이버는 사용자 정보를 {@code response} 객체로 한 번 감싸고, 카카오는
 * {@code kakao_account} 아래에 두며 이메일 제공이 선택 동의다. 그 차이를 서비스 계층까지
 * 올리지 않는다.
 *
 * <p><b>구현체는 클라이언트 ID/시크릿이 없어도 빈으로 등록된다.</b> 없는 제공자를
 * 빈에서 빼버리면 "왜 카카오 버튼이 없지"를 설정이 아니라 코드에서 찾게 된다.
 * 대신 {@link #isConfigured()} 가 false 를 돌려주고, 화면은 사용할 수 있는 것만 노출한다.
 */
public interface OAuthProvider {

    OAuthProviderType type();

    /**
     * 이 제공자를 지금 쓸 수 있는가.
     *
     * <p>false 인 이유는 대개 앱 등록·검수(TODO A-4)가 끝나지 않아 환경변수가 비어 있는 것이다.
     */
    boolean isConfigured();

    /**
     * 사용자를 보낼 제공자 로그인 페이지 URL.
     *
     * @param state       CSRF 방지용 값. 콜백에서 그대로 돌아온다
     * @param redirectUri 제공자에 <b>사전 등록된 값과 정확히 같아야</b> 한다. 다르면 제공자가 거부한다
     */
    String authorizeUrl(String state, String redirectUri);

    /**
     * 인가코드를 사용자 정보로 바꾼다 (토큰 교환 + 프로필 조회).
     *
     * @throws OAuthException 교환 실패, 응답 형식 이상, 식별자 누락
     */
    OAuthUserInfo fetchUserInfo(String code, String state, String redirectUri);
}
