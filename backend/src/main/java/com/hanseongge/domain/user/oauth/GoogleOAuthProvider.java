package com.hanseongge.domain.user.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.global.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 구글 로그인.
 *
 * <p>사용자 정보가 최상위에 평평하게 온다. 식별자는 {@code sub} 이며 OIDC 표준값이다.
 */
@Component
public class GoogleOAuthProvider extends AbstractOAuthProvider {

    public GoogleOAuthProvider(AppProperties appProperties, RestClient oauthRestClient) {
        super(appProperties.oauth().google(), oauthRestClient);
    }

    @Override
    public OAuthProviderType type() {
        return OAuthProviderType.GOOGLE;
    }

    @Override
    protected String authorizationEndpoint() {
        return "https://accounts.google.com/o/oauth2/v2/auth";
    }

    @Override
    protected String tokenEndpoint() {
        return "https://oauth2.googleapis.com/token";
    }

    @Override
    protected String userInfoEndpoint() {
        return "https://openidconnect.googleapis.com/v1/userinfo";
    }

    @Override
    protected String scope() {
        // 이메일과 기본 프로필만. 그 이상은 받지 않는다.
        return "openid email profile";
    }

    @Override
    protected OAuthUserInfo parseUserInfo(JsonNode body) {
        String sub = textOrNull(body, "sub");
        if (sub == null) {
            throw new OAuthException("구글 응답에 sub 가 없습니다");
        }
        return new OAuthUserInfo(type(), sub, textOrNull(body, "email"), textOrNull(body, "name"));
    }
}
