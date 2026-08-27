package com.hanseongge.domain.user.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.global.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 카카오 로그인.
 *
 * <p>이메일이 <b>선택 동의</b>라 사용자가 거부하면 아예 오지 않는다. 그래서 이메일을
 * 필수로 다루면 카카오 가입이 통째로 막힌다 — {@code users.email} 이 nullable 인 이유다.
 *
 * <p>닉네임은 {@code kakao_account.profile.nickname} 에 있다. 최상위
 * {@code properties.nickname} 은 예전 경로로 비어 있을 수 있어 둘 다 본다.
 */
@Component
public class KakaoOAuthProvider extends AbstractOAuthProvider {

    public KakaoOAuthProvider(AppProperties appProperties, RestClient oauthRestClient) {
        super(appProperties.oauth().kakao(), oauthRestClient);
    }

    @Override
    public OAuthProviderType type() {
        return OAuthProviderType.KAKAO;
    }

    @Override
    protected String authorizationEndpoint() {
        return "https://kauth.kakao.com/oauth/authorize";
    }

    @Override
    protected String tokenEndpoint() {
        return "https://kauth.kakao.com/oauth/token";
    }

    @Override
    protected String userInfoEndpoint() {
        return "https://kapi.kakao.com/v2/user/me";
    }

    @Override
    protected String scope() {
        return "account_email profile_nickname";
    }

    @Override
    protected OAuthUserInfo parseUserInfo(JsonNode body) {
        String id = textOrNull(body, "id");
        if (id == null) {
            throw new OAuthException("카카오 응답에 id 가 없습니다");
        }
        JsonNode account = body.path("kakao_account");
        String name = textOrNull(account.path("profile"), "nickname");
        if (name == null) {
            name = textOrNull(body.path("properties"), "nickname");
        }
        // 동의하지 않으면 email 자체가 없거나 email_needs_agreement=true 로 온다.
        String email = account.path("email_needs_agreement").asBoolean(false)
                ? null
                : textOrNull(account, "email");
        return new OAuthUserInfo(type(), id, email, name);
    }
}
