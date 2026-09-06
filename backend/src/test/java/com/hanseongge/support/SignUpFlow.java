package com.hanseongge.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.user.entity.VerifiedIdentity;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가입 흐름을 끝까지 태우는 도우미.
 *
 * <p>본인확인이 들어오면서 가입이 <b>네 단계</b>가 됐다. 그 단계를 테스트마다 손으로
 * 이어 붙이면, 흐름이 바뀔 때마다 여러 파일을 같이 고쳐야 하고 한 곳을 빠뜨리면
 * 그 테스트만 이상하게 실패한다.
 *
 * <p>회원이 필요할 뿐인 테스트(게시판·이벤트)는 {@link #social} 한 줄만 부르면 된다.
 * 단계별 검증이 목적인 테스트는 이것을 쓰지 않고 직접 부른다.
 */
public final class SignUpFlow {

    /** 복귀 주소는 CORS 허용 목록 안에 있어야 한다 — 공개 리다이렉트를 막는 검사가 있다. */
    public static final String RETURN_URL = "http://localhost:3000/auth/identity";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final StubOAuthProvider google;
    private final StubIdentityVerificationProvider identity;

    public SignUpFlow(MockMvc mockMvc, ObjectMapper objectMapper,
                      StubOAuthProvider google, StubIdentityVerificationProvider identity) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
        this.google = google;
        this.identity = identity;
    }

    /** 소셜 가입을 끝내고 Access 토큰을 돌려준다. 신원은 매번 새 DI 다. */
    public String social(String providerUserId, String email, String name) throws Exception {
        return social(providerUserId, email, name, null);
    }

    /** 신원을 지정한다 — 중복가입·계정 병합을 시험할 때 쓴다. */
    public String social(String providerUserId, String email, String name,
                         VerifiedIdentity verifiedIdentity) throws Exception {
        google.willReturn(providerUserId, email, name);

        // 회원 이름은 이제 <b>본인확인 실명</b>이다. 소셜 닉네임이 아니다.
        // 그래서 신원을 따로 지정하지 않으면 여기서 같은 이름으로 맞춰 준다 —
        // 안 그러면 "김철수 → 김**" 같은 마스킹 검증이 스텁 기본 이름을 보게 된다.
        identity.nextIdentity = verifiedIdentity != null
                ? verifiedIdentity
                : UserTestFixtures.identity(name);

        String state = read(post("/api/v1/auth/oauth/GOOGLE/authorize")).get("state").asText();

        JsonNode callback = read(post("/api/v1/auth/oauth/GOOGLE/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", "code-" + providerUserId, "state", state))));

        JsonNode afterIdentity = read(post("/api/v1/auth/identity/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "signUpToken", callback.get("signUpToken").asText(),
                        "providerToken", "stub-token"))));

        // 이미 가입한 신원이면 여기서 바로 로그인된다 (계정 병합).
        if (afterIdentity.hasNonNull("accessToken")) {
            return afterIdentity.get("accessToken").asText();
        }

        JsonNode signedUp = read(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "signUpToken", afterIdentity.get("signUpToken").asText(),
                        "agreements", allRequiredAgreed()))));
        return signedUp.get("accessToken").asText();
    }

    /** 필수는 동의, 선택은 거부. 선택을 빼먹지 않고 false 로 보내는 것이 실제 화면 동작이다. */
    public static List<Map<String, Object>> allRequiredAgreed() {
        return List.of(
                Map.of("type", "TERMS", "agreed", true),
                Map.of("type", "PRIVACY", "agreed", true),
                Map.of("type", "AGE_14", "agreed", true),
                Map.of("type", "MARKETING", "agreed", false),
                Map.of("type", "THIRD_PARTY", "agreed", false));
    }

    private JsonNode read(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder)
            throws Exception {
        MvcResult result = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(new LinkedHashMap<>(body));
    }
}
