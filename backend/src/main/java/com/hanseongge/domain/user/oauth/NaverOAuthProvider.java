package com.hanseongge.domain.user.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.global.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 네이버 로그인.
 *
 * <p>다른 두 곳과 다른 점이 둘 있다.
 * <ul>
 *   <li>사용자 정보가 {@code response} 객체로 한 겹 감싸여 온다.</li>
 *   <li>{@code resultcode} 가 "00" 이 아니면 <b>HTTP 200 인데도 실패</b>다. 상태코드만 보고
 *       성공으로 넘기면 빈 프로필로 가입이 진행된다.</li>
 * </ul>
 */
@Component
public class NaverOAuthProvider extends AbstractOAuthProvider {

    public NaverOAuthProvider(AppProperties appProperties, RestClient oauthRestClient) {
        super(appProperties.oauth().naver(), oauthRestClient);
    }

    @Override
    public OAuthProviderType type() {
        return OAuthProviderType.NAVER;
    }

    @Override
    protected String authorizationEndpoint() {
        return "https://nid.naver.com/oauth2.0/authorize";
    }

    @Override
    protected String tokenEndpoint() {
        return "https://nid.naver.com/oauth2.0/token";
    }

    @Override
    protected String userInfoEndpoint() {
        return "https://openapi.naver.com/v1/nid/me";
    }

    @Override
    protected String scope() {
        // 네이버는 개발자센터에서 제공 항목을 설정한다. 요청 파라미터로 범위를 보내지 않는다.
        return null;
    }

    @Override
    protected OAuthUserInfo parseUserInfo(JsonNode body) {
        String resultCode = textOrNull(body, "resultcode");
        if (!"00".equals(resultCode)) {
            throw new OAuthException("네이버 사용자 정보 조회 실패 resultcode=" + resultCode);
        }
        JsonNode response = body.path("response");
        String id = textOrNull(response, "id");
        if (id == null) {
            throw new OAuthException("네이버 응답에 id 가 없습니다");
        }
        return new OAuthUserInfo(type(), id, textOrNull(response, "email"), textOrNull(response, "name"));
    }
}
