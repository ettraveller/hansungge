package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.dto.AuthProvidersResponse;
import com.hanseongge.domain.user.dto.OAuthAuthorizeResponse;
import com.hanseongge.domain.user.dto.SignInResponse;
import com.hanseongge.domain.user.dto.SignUpRequest;
import com.hanseongge.domain.user.entity.AgreementType;
import com.hanseongge.domain.user.dto.IdentityStartResponse;
import com.hanseongge.domain.user.entity.EmailVerificationPurpose;
import com.hanseongge.domain.user.entity.IdentityPurpose;
import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.domain.user.entity.SocialAccount;
import com.hanseongge.domain.user.oauth.OAuthException;
import com.hanseongge.domain.user.oauth.OAuthProvider;
import com.hanseongge.domain.user.oauth.OAuthProviderRegistry;
import com.hanseongge.domain.user.oauth.OAuthUserInfo;
import com.hanseongge.domain.user.repository.SocialAccountRepository;
import com.hanseongge.global.auth.RequestInfo;
import com.hanseongge.global.config.AppProperties;
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
    private final IdentityVerificationService identityVerificationService;
    private final EmailVerificationService emailVerificationService;
    private final UserLoginAttemptService loginAttemptService;
    private final AppProperties appProperties;
    private final com.hanseongge.domain.user.repository.UserRepository userRepository;

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

        // 계정을 아직 만들지 않는다. 본인확인과 약관 동의가 남았다.
        log.info("[AUTH] 신규 소셜 신원 확인. provider={} 본인확인 대기", info.provider());
        PendingSignUp pending = new PendingSignUp(info, info.email(), null, null);
        return SignInResponse.identityRequired(
                tokenFactory.issuePendingToken(pending),
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
        PendingSignUp pending = tokenFactory.readPendingToken(request.signUpToken());

        // 본인확인을 거치지 않은 토큰으로는 가입할 수 없다. 소셜도 예외가 아니다 —
        // 한쪽만 면제하면 "본인확인을 안 한 회원"이 생기고, 그 예외가 코드 전체로 번진다.
        if (!pending.isIdentityVerified()) {
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_INVALID,
                    "본인확인을 먼저 마쳐야 합니다.");
        }
        identityVerificationService.requireUsable(pending.identityVerificationId(), IdentityPurpose.SIGNUP);
        if (pending.emailVerificationId() != null) {
            emailVerificationService.requireVerified(pending.emailVerificationId());
        }

        Map<AgreementType, Boolean> choices = normalizeChoices(request.agreements());
        requireAllMandatoryAgreed(choices);
        return accountService.createAccount(
                pending.toMaterial(request.password()), choices, requestInfo, response);
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
    // 자체 가입 — 이메일 인증 → 본인확인 → 약관
    // =================================================================

    /**
     * 이메일로 인증번호를 보낸다.
     *
     * <p>이미 가입된 주소인지 여기서 알려주지 않는다. 알려주면 <b>회원 명단을 훑는 도구</b>가
     * 된다 — 아무 주소나 넣어 응답이 다른지 보면 된다. 중복 판정은 가입을 마치는 시점에 한다.
     */
    public void sendEmailCode(String email, RequestInfo requestInfo) {
        emailVerificationService.issue(email, EmailVerificationPurpose.SIGNUP, requestInfo);
    }

    /**
     * 인증번호를 확인하고 다음 단계로 넘어갈 토큰을 준다.
     *
     * <p>{@code confirm} 이 예외 대신 boolean 을 돌려주는 이유는 시도 횟수 때문이다 —
     * 트랜잭션 안에서 예외를 던지면 증가한 횟수가 롤백되어 제한이 걸리지 않는다.
     */
    public String confirmEmailCode(String email, String code) {
        if (!emailVerificationService.confirm(email, code, EmailVerificationPurpose.SIGNUP)) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }
        var verification = emailVerificationService
                .findLatestVerified(email, EmailVerificationPurpose.SIGNUP)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_VERIFICATION_INVALID));

        PendingSignUp pending = new PendingSignUp(null, email, verification.getId(), null);
        return tokenFactory.issuePendingToken(pending);
    }

    // =================================================================
    // 본인확인 (자체·소셜 공통)
    // =================================================================

    /**
     * 본인확인 창 주소.
     *
     * <p>{@code requestId} 로 가입 토큰을 그대로 쓴다. 별도 식별자를 만들어 서버에 보관하면
     * 그 보관소를 또 관리해야 하는데, 토큰 자체가 이미 서명되어 있고 만료도 있다.
     */
    public IdentityStartResponse startIdentityVerification(String signUpToken, String returnUrl) {
        // 토큰이 유효한지 먼저 본다. 아니면 인증창까지 갔다가 돌아와서야 실패한다.
        tokenFactory.readPendingToken(signUpToken);
        requireAllowedReturnUrl(returnUrl);
        return new IdentityStartResponse(
                identityVerificationService.startUrl(signUpToken, returnUrl), signUpToken);
    }

    /**
     * 복귀 주소를 우리 사이트로 제한한다.
     *
     * <p>거르지 않으면 <b>공개 리다이렉트</b>가 된다 — 공격자가 우리 도메인의 링크를
     * 보내 놓고 본인확인 뒤에 자기 사이트로 보낼 수 있고, 그 주소에는 가입 토큰이
     * 쿼리로 붙어 있다. 우리 도메인에서 출발했으니 사용자는 의심하지 않는다.
     *
     * <p>허용 목록은 CORS 와 같은 값을 쓴다. 브라우저가 우리 API 를 부를 수 있는 출처와
     * 사용자를 돌려보낼 수 있는 출처가 다를 이유가 없고, 목록을 둘로 두면 한쪽만
     * 고치는 사고가 난다.
     */
    private void requireAllowedReturnUrl(String returnUrl) {
        boolean allowed = appProperties.cors().allowedOrigins().stream()
                .anyMatch(origin -> returnUrl.equals(origin) || returnUrl.startsWith(origin + "/"));
        if (!allowed) {
            log.warn("[AUTH] 허용되지 않은 복귀 주소: {}", returnUrl);
            throw new BusinessException(ErrorCode.INVALID_INPUT, "허용되지 않은 복귀 주소입니다.");
        }
    }

    /**
     * 본인확인 결과를 받아 다음 갈 곳을 정한다.
     *
     * <p>여기가 <b>계정 병합이 일어나는 지점</b>이다. 약관까지 다 받은 뒤에 "이미 회원이시네요"라고
     * 하는 것은 나쁜 흐름이라, DI 를 손에 넣는 순간 판정한다.
     *
     * <ul>
     *   <li>같은 DI 로 이미 가입 → 로그인 수단만 붙이고 그 계정으로 들여보낸다
     *   <li>처음 보는 DI → 약관 화면으로 보낼 토큰을 준다
     * </ul>
     */
    public SignInResponse completeIdentityVerification(String signUpToken, String providerToken,
                                                       String rawPassword, RequestInfo requestInfo,
                                                       HttpServletResponse response) {
        PendingSignUp pending = tokenFactory.readPendingToken(signUpToken);

        // 외부 호출이 먼저다. 트랜잭션 안에서 부르면 공급자가 느릴 때 커넥션을 붙잡는다.
        var verification = identityVerificationService.verifyAndRecord(
                signUpToken, providerToken, IdentityPurpose.SIGNUP, requestInfo);

        Optional<SignInResponse> merged = accountService.mergeIfExisting(
                verification.getId(), pending.social(), rawPassword, response);
        if (merged.isPresent()) {
            return merged.get();
        }

        PendingSignUp next = pending.withIdentity(verification.getId());
        return SignInResponse.signUpRequired(
                tokenFactory.issuePendingToken(next),
                agreementCatalog.all(),
                verification.getName(),
                next.email());
    }

    // =================================================================
    // 이메일 로그인
    // =================================================================

    /**
     * 비밀번호 로그인.
     *
     * <p>실패 사유를 사용자에게 세분해 알려주지 않는다 — "이메일이 없다"와 "비밀번호가
     * 틀렸다"를 구분해 주면 가입 여부를 확인하는 데 쓰인다. 다만 <b>비밀번호가 없는
     * 계정</b>과 <b>잠긴 계정</b>은 알려준다. 그 둘은 사용자가 다음에 할 일이 다르고,
     * 알려주지 않으면 맞는 비밀번호를 계속 넣어보게 된다.
     */
    public SignInResponse signInWithPassword(String email, String rawPassword,
                                             HttpServletResponse response) {
        UserLoginAttemptService.Outcome outcome = loginAttemptService.attempt(email, rawPassword);

        switch (outcome.result()) {
            case SUCCESS -> { }
            case NO_PASSWORD -> throw new BusinessException(ErrorCode.PASSWORD_NOT_SET);
            case LOCKED -> throw new BusinessException(ErrorCode.USER_ACCOUNT_LOCKED);
            case NOT_ALLOWED -> outcome.user().requireSignInAllowed();
            case BAD_CREDENTIALS -> throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return accountService.signInWithPassword(outcome.user().getId(), response);
    }

    // =================================================================
    // 비밀번호 재설정
    // =================================================================

    /**
     * 재설정 인증번호를 보낸다.
     *
     * <p><b>가입 여부를 알려주지 않는다.</b> 회원이든 아니든 같은 응답을 준다 —
     * 알려주면 아무 주소나 넣어 회원 명단을 훑을 수 있다.
     *
     * <p>회원이 아니어도 인증 행을 남기는 이유는 <b>재발송 쿨다운을 같게 만들기</b>
     * 위해서다. 행을 남기지 않으면 두 번 연속 요청했을 때 회원이면 429, 아니면 204 가
     * 되어 응답으로 구분된다. 메일만 보내지 않는다.
     */
    public void requestPasswordReset(String email, RequestInfo requestInfo) {
        boolean isMember = userRepository.findByEmail(email).isPresent();
        emailVerificationService.issue(
                email, EmailVerificationPurpose.PASSWORD_RESET, requestInfo, isMember);
    }

    /**
     * 인증번호를 확인하고 비밀번호를 바꾼다.
     *
     * <p>여기서도 회원이 아닌 경우와 번호가 틀린 경우를 같은 오류로 돌려준다.
     */
    public void confirmPasswordReset(String email, String code, String newPassword) {
        if (!emailVerificationService.confirm(email, code, EmailVerificationPurpose.PASSWORD_RESET)) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_VERIFICATION_INVALID));
        user.requireSignInAllowed();

        accountService.resetPassword(user.getId(), newPassword);
        emailVerificationService.findLatestVerified(email, EmailVerificationPurpose.PASSWORD_RESET)
                .ifPresent(v -> emailVerificationService.consume(v.getId()));
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
