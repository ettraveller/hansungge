package com.hanseongge.domain.user.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.hanseongge.global.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 세 제공자가 공유하는 OAuth2 Authorization Code 골격.
 *
 * <p>같은 부분(인가 URL 조립, 토큰 교환 POST, 실패 처리)만 여기 두고, 다른 부분
 * (엔드포인트 주소, 사용자 정보 응답 모양)은 하위 클래스가 채운다.
 */
@Slf4j
public abstract class AbstractOAuthProvider implements OAuthProvider {

    protected final AppProperties.OAuthClient client;
    protected final RestClient restClient;

    protected AbstractOAuthProvider(AppProperties.OAuthClient client, RestClient restClient) {
        this.client = client;
        this.restClient = restClient;
    }

    @Override
    public boolean isConfigured() {
        return client != null && client.isConfigured();
    }

    /** 제공자 로그인 페이지 */
    protected abstract String authorizationEndpoint();

    /** 인가코드 → 액세스 토큰 */
    protected abstract String tokenEndpoint();

    /** 액세스 토큰 → 사용자 정보 */
    protected abstract String userInfoEndpoint();

    /** 요청할 권한 범위. 제공자마다 표기가 달라 하위 클래스가 정한다. */
    protected abstract String scope();

    /** 제공자별 사용자 정보 응답을 공통 형태로 옮긴다. */
    protected abstract OAuthUserInfo parseUserInfo(JsonNode body);

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        requireConfigured();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authorizationEndpoint())
                .queryParam("response_type", "code")
                .queryParam("client_id", encode(client.clientId()))
                .queryParam("redirect_uri", encode(redirectUri))
                .queryParam("state", encode(state));
        if (scope() != null) {
            builder.queryParam("scope", encode(scope()));
        }
        // build(true): 위에서 직접 인코딩했으므로 두 번 인코딩되지 않게 한다.
        // 이중 인코딩된 redirect_uri 는 제공자가 "등록되지 않은 주소"로 거부한다.
        return builder.build(true).toUriString();
    }

    @Override
    public OAuthUserInfo fetchUserInfo(String code, String state, String redirectUri) {
        requireConfigured();
        String accessToken = exchangeCodeForToken(code, state, redirectUri);
        return parseUserInfo(requestUserInfo(accessToken));
    }

    private String exchangeCodeForToken(String code, String state, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", client.clientId());
        form.add("client_secret", client.clientSecret());
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        // 네이버는 state 를 토큰 요청에도 요구한다. 다른 제공자는 무시하므로 항상 보낸다.
        form.add("state", state);

        JsonNode body;
        try {
            body = restClient.post()
                    .uri(tokenEndpoint())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            // 원문에는 client_id 가 섞여 나오는 경우가 있어 로그에만 남긴다.
            log.warn("[OAUTH] {} 토큰 교환 실패: {}", type(), e.getMessage());
            throw new OAuthException(type() + " 토큰 교환 실패", e);
        }

        if (body == null || !body.hasNonNull("access_token")) {
            log.warn("[OAUTH] {} 토큰 응답에 access_token 이 없습니다. error={}",
                    type(), body == null ? "(본문 없음)" : body.path("error").asText("(없음)"));
            throw new OAuthException(type() + " 토큰 응답이 올바르지 않습니다");
        }
        return body.get("access_token").asText();
    }

    private JsonNode requestUserInfo(String accessToken) {
        try {
            JsonNode body = restClient.get()
                    .uri(userInfoEndpoint())
                    .header("Authorization", "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new OAuthException(type() + " 사용자 정보 응답이 비어 있습니다");
            }
            return body;
        } catch (RestClientException e) {
            log.warn("[OAUTH] {} 사용자 정보 조회 실패: {}", type(), e.getMessage());
            throw new OAuthException(type() + " 사용자 정보 조회 실패", e);
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new OAuthException(type() + " 클라이언트 설정이 없습니다");
        }
    }

    /** 값이 없으면 빈 문자열을 돌려준다 — null 이 URL 에 "null" 로 박히는 것을 막는다. */
    protected static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
