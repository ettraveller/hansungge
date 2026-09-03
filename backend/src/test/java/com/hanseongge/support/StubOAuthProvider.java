package com.hanseongge.support;

import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.domain.user.oauth.OAuthException;
import com.hanseongge.domain.user.oauth.OAuthProvider;
import com.hanseongge.domain.user.oauth.OAuthUserInfo;

/**
 * 소셜 제공자 테스트 대역.
 *
 * <p>실제 구글·네이버·카카오 서버를 부를 수 없으니 {@link OAuthProvider} 경계에서 끊는다.
 * 이것이 외부 연동을 인터페이스 뒤에 두는(원칙 P5) 실질적인 이득이다 — 인증 흐름 전체를
 * 네트워크 없이 검증할 수 있다.
 *
 * <p>Mockito 로 대체하지 않는 이유: {@code OAuthProviderRegistry} 가 기동 시점에
 * {@link #type()} 으로 맵을 만드는데, 목 객체는 그 시점에 아직 스텁이 걸려 있지 않아
 * enum 대신 null 을 돌려준다. 그러면 조회가 통째로 어긋난다.
 */
public class StubOAuthProvider implements OAuthProvider {

    private final OAuthProviderType type;

    /** 이 제공자를 "설정됨"으로 볼지. 미설정 시 503 을 확인할 때 false 로 둔다. */
    public boolean configured = true;
    /** 다음 교환에서 돌려줄 신원. */
    public OAuthUserInfo nextUserInfo;
    /** null 이 아니면 교환 시 이 예외를 던진다 (제공자 장애 재현). */
    public RuntimeException nextFailure;
    /** 마지막으로 넘겨받은 redirect_uri. 인가 요청과 교환에서 같은지 확인한다. */
    public String lastRedirectUri;
    public String lastState;

    public StubOAuthProvider(OAuthProviderType type) {
        this.type = type;
        this.nextUserInfo = new OAuthUserInfo(type, "stub-user-1", "buyer@test.local", "테스트 구매자");
    }

    @Override
    public OAuthProviderType type() {
        return type;
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        lastState = state;
        lastRedirectUri = redirectUri;
        return "https://stub.oauth.test/authorize?state=" + state + "&redirect_uri=" + redirectUri;
    }

    @Override
    public OAuthUserInfo fetchUserInfo(String code, String state, String redirectUri) {
        lastState = state;
        lastRedirectUri = redirectUri;
        if (nextFailure != null) {
            RuntimeException failure = nextFailure;
            nextFailure = null;
            throw failure;
        }
        if ("bad-code".equals(code)) {
            throw new OAuthException("스텁: 잘못된 인가코드");
        }
        return nextUserInfo;
    }

    /** 다른 사람으로 로그인하는 상황을 만든다. */
    public void willReturn(String providerUserId, String email, String name) {
        this.nextUserInfo = new OAuthUserInfo(type, providerUserId, email, name);
    }

    public void reset() {
        configured = true;
        nextFailure = null;
        willReturn("stub-user-1", "buyer@test.local", "테스트 구매자");
    }
}
