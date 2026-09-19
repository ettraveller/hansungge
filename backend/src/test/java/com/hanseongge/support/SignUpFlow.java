package com.hanseongge.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.user.entity.VerifiedIdentity;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가입 흐름을 끝까지 태우는 도우미.
 *
 * <p>가입은 <b>약관 → 본인확인(인증창 → 수신 주소 → 결과 제출) → 비밀번호</b>로 여러 단계다.
 * 그 단계를 테스트마다 손으로 이어 붙이면 흐름이 바뀔 때마다 여러 파일을 같이 고쳐야 한다.
 *
 * <p>회원이 필요할 뿐인 테스트(게시판·이벤트)는 {@link #social} 한 줄만 부르면 된다.
 * 단계별 검증이 목적인 테스트는 {@link #agree}·{@link #verifyIdentity} 같은 조각을 쓴다.
 */
public final class SignUpFlow {

    public static final String PASSWORD = "Test-pass-1234!";

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

    // ---------- 한 줄짜리 ----------

    /** 소셜 가입을 끝내고 Access 토큰을 돌려준다. 신원은 매번 새 DI·새 번호다. */
    public String social(String providerUserId, String email, String name) throws Exception {
        return social(providerUserId, email, name, UserTestFixtures.identity(name));
    }

    /**
     * 신원을 지정한다. 같은 DI 가 이미 있으면 연결 확인 화면에서 [연결하기]를 누른 것과 같다.
     *
     * <p>회원 이름은 <b>본인확인 실명</b>이다 — 소셜 닉네임이 아니다. 마스킹 검증이 스텁 기본
     * 이름을 보지 않도록 신원 이름을 맞춰 준다.
     */
    public String social(String providerUserId, String email, String name,
                         VerifiedIdentity verifiedIdentity) throws Exception {
        String signUpToken = socialCallback(providerUserId, email, name).get("signUpToken").asText();
        String agreed = agree(signUpToken);
        identity.willReturn(verifiedIdentity);
        JsonNode result = verifyIdentity(agreed);

        return switch (result.get("step").asText()) {
            case "SIGNED_IN" -> result.get("session").get("accessToken").asText();
            case "CONFIRM_LINK" -> read(post("/api/v1/auth/signup/link")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("signUpToken", result.get("signUpToken").asText()))))
                    .get("accessToken").asText();
            default -> throw new IllegalStateException("예상하지 못한 단계: " + result);
        };
    }

    /** 휴대폰(자체) 가입을 끝내고 Access 토큰을 돌려준다. */
    public String phone(VerifiedIdentity verifiedIdentity, String password) throws Exception {
        String agreed = agree(null);
        identity.willReturn(verifiedIdentity);
        JsonNode result = verifyIdentity(agreed);
        if (!"SET_PASSWORD".equals(result.get("step").asText())) {
            throw new IllegalStateException("예상하지 못한 단계: " + result);
        }
        return read(post("/api/v1/auth/signup/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of(
                        "signUpToken", result.get("signUpToken").asText(),
                        "password", password))))
                .get("accessToken").asText();
    }

    // ---------- 조각 ----------

    /** 소셜 로그인 → 콜백. 처음 보는 소셜이면 signUpRequired 와 signUpToken 이 온다. */
    public JsonNode socialCallback(String providerUserId, String email, String name) throws Exception {
        google.willReturn(providerUserId, email, name);
        String state = read(post("/api/v1/auth/oauth/GOOGLE/authorize")).get("state").asText();
        return read(post("/api/v1/auth/oauth/GOOGLE/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("code", "code-" + providerUserId, "state", state))));
    }

    /** 약관 동의(필수만). 소셜이면 콜백 토큰을, 자체 가입이면 null 을 넘긴다. */
    public String agree(String signUpToken) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        if (signUpToken != null) {
            body.put("signUpToken", signUpToken);
        }
        body.put("agreements", allRequiredAgreed());
        return read(post("/api/v1/auth/signup/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
                .get("signUpToken").asText();
    }

    /**
     * 본인확인 한 바퀴 — 인증창 폼을 받아, 공급자가 하듯 수신 주소로 보내고, 돌아온 화면 주소의
     * providerToken 으로 결과를 제출한다.
     *
     * @return {@code /identity/complete} 응답 (step·signUpToken·resetToken…)
     */
    public JsonNode verifyIdentity(String agreedSignUpToken) throws Exception {
        MvcResult result = verifyIdentityRaw(agreedSignUpToken);
        if (result.getResponse().getStatus() != 200) {
            throw new IllegalStateException("본인확인 결과 제출 실패: "
                    + result.getResponse().getContentAsString());
        }
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /**
     * {@link #verifyIdentity} 와 같지만 응답을 그대로 준다 — 상태코드나 로그인 쿠키
     * (소셜 가입이 여기서 끝날 때)를 보려는 테스트가 쓴다.
     */
    public MvcResult verifyIdentityRaw(String agreedSignUpToken) throws Exception {
        JsonNode started = read(post("/api/v1/auth/identity/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("signUpToken", agreedSignUpToken))));
        String providerToken = returnFromProvider(started.get("form"));
        return mockMvc.perform(post("/api/v1/auth/identity/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "token", started.get("token").asText(),
                                "providerToken", providerToken))))
                .andReturn();
    }

    /** 비밀번호 찾기 본인확인 한 바퀴. {@code /password/reset/identity/complete} 응답을 돌려준다. */
    public MvcResult verifyIdentityForReset(VerifiedIdentity verifiedIdentity) throws Exception {
        identity.willReturn(verifiedIdentity);
        JsonNode started = read(post("/api/v1/auth/password/reset/identity/start"));
        String providerToken = returnFromProvider(started.get("form"));
        return mockMvc.perform(post("/api/v1/auth/password/reset/identity/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "token", started.get("token").asText(),
                                "providerToken", providerToken))))
                .andReturn();
    }

    /**
     * 공급자가 인증을 마치고 폼 필드를 수신 주소로 보낸다 → 303 → 화면 주소의 providerToken.
     */
    public String returnFromProvider(JsonNode form) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/auth/identity/return")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);
        for (Map.Entry<String, JsonNode> field : form.get("fields").properties()) {
            request.param(field.getKey(), field.getValue().asText());
        }
        String location = mockMvc.perform(request)
                .andExpect(status().isSeeOther())
                .andReturn().getResponse().getHeader("Location");
        return UriComponentsBuilder.fromUriString(location).build()
                .getQueryParams().getFirst("providerToken");
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

    private JsonNode read(MockHttpServletRequestBuilder builder) throws Exception {
        MvcResult result = mockMvc.perform(builder).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String json(Map<String, Object> body) throws Exception {
        return objectMapper.writeValueAsString(new LinkedHashMap<>(body));
    }
}
