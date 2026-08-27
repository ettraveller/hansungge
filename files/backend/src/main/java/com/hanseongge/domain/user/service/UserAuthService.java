package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.dto.AuthProvidersResponse;
import com.hanseongge.domain.user.dto.OAuthAuthorizeResponse;
import com.hanseongge.domain.user.dto.SignInResponse;
import com.hanseongge.domain.user.dto.SignUpRequest;
import com.hanseongge.domain.user.entity.AgreementType;
import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.domain.user.entity.SocialAccount;
import com.hanseongge.domain.user.oauth.OAuthException;
import com.hanseongge.domain.user.oauth.OAuthProvider;
import com.hanseongge.domain.user.oauth.OAuthProviderRegistry;
import com.hanseongge.domain.user.oauth.OAuthUserInfo;
import com.hanseongge.domain.user.repository.SocialAccountRepository;
import com.hanseongge.global.auth.RequestInfo;
import com.hanseongge.global.auth.UserJwtProvider;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 회원 소셜 로그인·가입 흐름 (개발계획 §7).
 *
 * <h2>흐름</h2>
 * <pre>
 * 1. authorize()  → 제공자 로그인 URL + state 발급
 * 2. (브라우저가 제공자에 다녀와 code 를 들고 돌아온다)
 * 3. callback()   → state 검증 → 인가코드 교환 → 소셜 신원 확보
 *                   ├ 기존 회원  → 토큰 발급, 끝
 *                   └ 신규      → signUpToken + 약관 목록 반환 (계정은 아직 없다)
 * 4. signUp()     → 필수 약관 확인 → users + social_accounts + user_agreements 한 트랜잭션
 * </pre>
 *
 * <h2>왜 3단계에서 바로 가입시키지 않는가</h2>
 * 계정을 먼저 만들고 약관을 나중에 받으면, 그 사이 <b>동의 없이 개인정보를 보관한 상태</b>가
 * 된다. 사용자가 약관 화면에서 이탈하면 그 계정은 동의 없는 유령 계정으로 남는다.
 * 그래서 인증 결과를 DB 가 아니라 서명 토큰에 담아 두고, 동의가 끝나는 순간에 만든다.
 *
 * <h2>이메일로 계정을 찾지 않는다</h2>
 * 조회 키는 {@code (provider, provider_user_id)} 뿐이다 (§7.2). 이메일로 자동 병합하면
 * "피해자 이메일로 다른 소셜 계정을 만들어 기존 계정을 흡수"하는 경로가 열린다.
 * 계정 통합은 로그인한 상태에서 본인이 마이페이지에서 한다.
 *
 * <h2>이 클래스에는 트랜잭션이 없다</h2>
 * 안에 외부 HTTP 호출이 있어서다. 상태 변경은 전부 {@link UserAccountService} 가 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthService {

    private final OAuthProviderRegistry registry;
    private final OAuthTokenFactory tokenFactory;
    private final AgreementCatalog agreementCatalog;
    private final SocialAccountRepository socialAccountRepository;
    private final UserAccountService accountService;
    private final UserRefreshTokenService refreshTokenService;
    private final UserJwtProvider jwtProvider;

    // =================================================================
    // 로그인 화면 구성
    // =================================================================

    public AuthProvidersResponse describeProviders() {
        List<AuthProvidersResponse.ProviderItem> providers = registry.available().stream()
                .map(type -> new AuthProvidersResponse.ProviderItem(type, type.displayName()))
                .toList();
        return new AuthProvidersResponse(providers, agreementCatalog.all());
    }

    // =================================================================
    // 1. 인가 요청
    // =================================================================

    public OAuthAuthorizeResponse authorize(OAuthProviderType type) {
        OAuthProvider provider = registry.require(type);
        String state = tokenFactory.issueState(type);
        return new OAuthAuthorizeResponse(
                provider.authorizeUrl(state, registry.redirectUri(type)), state);
    }

    // =================================================================
    // 2. 콜백
    // =================================================================

    public SignInResponse callback(OAuthProviderType type, String code, String state,
                                   HttpServletResponse response) {
        tokenFactory.verifyState(state, type);
        OAuthUserInfo info = exchange(type, code, state);

        Optional<SocialAccount> linked = socialAccountRepository
                .findByProviderAndProviderUserId(info.provider(), info.providerUserId());

        if (linked.isPresent()) {
            return accountService.signIn(linked.get().getId(), info, response);
        }

        log.info("[AUTH] 신규 소셜 신원 확인. provider={} 가입 대기", info.provider());
        return SignInResponse.signUpRequired(
                tokenFactory.issueSignUpToken(info),
                agreementCatalog.all(),
                info.name(),
                info.email());
    }

    /**
     * 제공자 호출 실패를 도메인 오류로 바꾼다.
     *
     * <p>원문을 그대로 내보내지 않는 이유: 제공자 오류 본문에는 클라이언트 ID 나
     * 내부 파라미터가 섞여 나오는 경우가 있다.
     */
    public OAuthUserInfo exchange(OAuthProviderType type, String code, String state) {
        OAuthProvider provider = registry.require(type);
        try {
            return provider.fetchUserInfo(code, state, registry.redirectUri(type));
        } catch (OAuthException e) {
            log.warn("[AUTH] {} 인가코드 교환 실패: {}", type, e.getMessage());
            throw new BusinessException(ErrorCode.OAUTH_EXCHANGE_FAILED, e.getMessage());
        }
    }

    // =================================================================
    // 3. 가입 완료
    // =================================================================

    public SignInResponse signUp(SignUpRequest request, RequestInfo requestInfo,
                                 HttpServletResponse response) {
        OAuthUserInfo info = tokenFactory.readSignUpToken(request.signUpToken());
        Map<AgreementType, Boolean> choices = normalizeChoices(request.agreements());
        requireAllMandatoryAgreed(choices);
        return accountService.createAccount(info, choices, request.name(), requestInfo, response);
    }

    /**
     * 중복 선택을 정리한다. 같은 약관이 두 번 오면 <b>거부가 이긴다</b> —
     * 실수로 두 번 담겼을 때 동의로 처리하는 쪽이 훨씬 위험하다.
     */
    private Map<AgreementType, Boolean> normalizeChoices(List<SignUpRequest.AgreementChoice> choices) {
        Map<AgreementType, Boolean> map = new EnumMap<>(AgreementType.class);
        for (SignUpRequest.AgreementChoice choice : choices) {
            map.merge(choice.type(), choice.agreed(), (a, b) -> a && b);
        }
        return map;
    }

    private void requireAllMandatoryAgreed(Map<AgreementType, Boolean> choices) {
        List<AgreementType> missing = AgreementType.requiredTypes().stream()
                .filter(type -> !Boolean.TRUE.equals(choices.get(type)))
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        // 어떤 약관이 빠졌는지 짚어준다. "필수 항목에 동의해 주세요"만으로는
        // 사용자가 무엇을 눌러야 하는지 모른다.
        Map<String, String> errors = new LinkedHashMap<>();
        missing.forEach(type -> errors.put(type.name(), type.displayName() + "에 동의해야 합니다."));
        throw new BusinessException(ErrorCode.AGREEMENT_REQUIRED, "필수 약관 미동의: " + missing, errors);
    }

    // =================================================================
    // 세션 유지
    // =================================================================

    /** 쿠키의 Refresh 토큰으로 Access 토큰을 다시 발급한다. Refresh 도 함께 회전한다. */
    public SignInResponse refresh(String refreshToken, HttpServletResponse response) {
        Long userId = refreshTokenService.rotate(refreshToken, response);
        return accountService.describeSession(userId, jwtProvider.issueAccessToken(userId));
    }

    public void logout(String refreshToken, HttpServletResponse response) {
        refreshTokenService.revoke(refreshToken, response);
    }
}
