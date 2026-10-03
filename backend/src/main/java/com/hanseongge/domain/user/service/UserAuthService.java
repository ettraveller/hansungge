package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.dto.AgreementChoice;
import com.hanseongge.domain.user.dto.AuthProvidersResponse;
import com.hanseongge.domain.user.dto.IdentityResultResponse;
import com.hanseongge.domain.user.dto.IdentityStartResponse;
import com.hanseongge.domain.user.dto.OAuthAuthorizeResponse;
import com.hanseongge.domain.user.dto.PasswordResetGrantResponse;
import com.hanseongge.domain.user.dto.SignInResponse;
import com.hanseongge.domain.user.dto.SignUpCompleteRequest;
import com.hanseongge.domain.user.dto.UserResponse;
import com.hanseongge.domain.user.entity.AgreementType;
import com.hanseongge.domain.user.entity.IdentityPurpose;
import com.hanseongge.domain.user.entity.IdentityVerification;
import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.domain.user.entity.SocialAccount;
import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.entity.VerificationChannel;
import com.hanseongge.domain.user.entity.VerificationPurpose;
import com.hanseongge.domain.user.identity.IdentityMethod;
import com.hanseongge.domain.user.identity.IdentityStartForm;
import com.hanseongge.domain.user.oauth.OAuthException;
import com.hanseongge.domain.user.oauth.OAuthProvider;
import com.hanseongge.domain.user.oauth.OAuthProviderRegistry;
import com.hanseongge.domain.user.oauth.OAuthUserInfo;
import com.hanseongge.domain.user.repository.SocialAccountRepository;
import com.hanseongge.domain.user.repository.UserRepository;
import com.hanseongge.global.auth.RequestInfo;
import com.hanseongge.global.auth.UserJwtProvider;
import com.hanseongge.global.config.AppProperties;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import com.hanseongge.global.util.PhoneNumber;
import com.hanseongge.global.util.PrivacyMask;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 회원 로그인·가입 흐름 (개발계획 §7).
 *
 * <h2>로그인</h2>
 * <ul>
 *   <li>휴대폰번호 + 비밀번호 — 자체 가입자(본인인증으로 번호를 받은 회원)
 *   <li>휴대폰번호 + 문자 인증번호 — 본인인증으로 명의가 확인된 번호라, 로그인에서는 번호를
 *       가지고 있는지만 확인하면 된다
 *   <li>소셜(구글·네이버·카카오) — {@code (provider, provider_user_id)} 로만 찾는다
 * </ul>
 * 로그인에 간편인증을 쓰지 않는다 — 건당 과금이라 로그인마다 부르면 비용이 쌓인다.
 *
 * <h2>가입 (2026-09-21 개편)</h2>
 * <pre>
 * 자체   startSignUp(약관) → 간편인증 → SET_PASSWORD → completeSignUp → 로그인
 *                                    └ EXISTING_ACCOUNT → 로그인 / 바로 비밀번호 재설정
 * 소셜   callback → signUpSocial(약관) → 가입 완료 (본인인증 없음)
 * </pre>
 * 소셜 가입자의 신원은 매입·정산계좌 앞에서 로그인한 채 받는 본인인증({@link #startStepUp})으로 채운다.
 *
 * <h2>이메일로 계정을 찾지 않는다</h2>
 * 같은 사람 판정은 본인인증의 판정 키(CI 해시)로만 한다. 이메일이 같다는 이유로 합치면
 * "남의 이메일을 적어 둔 소셜 계정"으로 기존 계정을 가져가는 길이 된다.
 *
 * <h2>이 클래스에는 트랜잭션이 없다</h2>
 * 안에 외부 HTTP 호출(소셜·본인확인·문자)이 있어서다. 상태 변경은 {@link UserAccountService} 가 맡는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthService {

    /** 본인확인 공급자가 수신 주소로 보내는 값 중 화면까지 나를 것. 나머지는 버린다. */
    private static final Set<String> IDENTITY_RETURN_KEYS =
            Set.of("resultCode", "resultMsg", "txId", "authRequestUrl", "token", "mTxId");

    private final OAuthProviderRegistry registry;
    private final OAuthTokenFactory tokenFactory;
    private final AgreementCatalog agreementCatalog;
    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final UserAccountService accountService;
    private final UserRefreshTokenService refreshTokenService;
    private final UserJwtProvider jwtProvider;
    private final IdentityVerificationService identityVerificationService;
    private final VerificationCodeService codeService;
    private final UserLoginAttemptService loginAttemptService;
    private final AppProperties appProperties;

    // =================================================================
    // 로그인 화면 구성
    // =================================================================

    /**
     * 화면이 그릴 것 — 쓸 수 있는 소셜, 약관, 가입 가능 여부, 인증번호 로그인 가능 여부.
     *
     * <p>눌러야만 실패를 아는 버튼을 만들지 않으려고 서버가 알려준다.
     */
    public AuthProvidersResponse describeProviders() {
        List<AuthProvidersResponse.ProviderItem> providers = registry.available().stream()
                .map(type -> new AuthProvidersResponse.ProviderItem(type, type.displayName()))
                .toList();
        return new AuthProvidersResponse(providers, agreementCatalog.all(),
                identityVerificationService.isAvailable(), codeService.isSmsAvailable(),
                codeService.isMailAvailable());
    }

    // =================================================================
    // 소셜 로그인
    // =================================================================

    public OAuthAuthorizeResponse authorize(OAuthProviderType type) {
        OAuthProvider provider = registry.require(type);
        String state = tokenFactory.issueState(type);
        return new OAuthAuthorizeResponse(
                provider.authorizeUrl(state, registry.redirectUri(type)), state);
    }

    /**
     * 소셜 콜백.
     *
     * <p>이미 연결된 소셜 계정이면 로그인. 처음 보는 소셜 계정이면 <b>계정을 만들지 않고</b>
     * 약관 화면으로 보낼 토큰을 준다 — 약관 동의({@link #signUpSocial}) 뒤에 만든다.
     *
     * <p>이미 가입한 회원이 새 소셜로 들어오면 새 계정이 된다(중복가입 허용). 한 계정에 모으려면
     * 로그인한 뒤 마이페이지에서 연결한다.
     */
    public SignInResponse callback(OAuthProviderType type, String code, String state,
                                   HttpServletResponse response) {
        tokenFactory.verifyState(state, type);
        OAuthUserInfo info = exchange(type, code, state);

        Optional<SocialAccount> linked = socialAccountRepository
                .findByProviderAndProviderUserId(info.provider(), info.providerUserId());
        if (linked.isPresent()) {
            return accountService.signIn(linked.get().getId(), info, response);
        }

        log.info("[AUTH] 처음 보는 소셜 계정. provider={} 약관 대기", info.provider());
        return SignInResponse.signUpRequired(
                tokenFactory.issuePendingToken(PendingSignUp.social(info)),
                agreementCatalog.all(),
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
    // 가입 1 — 약관
    // =================================================================

    /**
     * 자체 가입 — 약관 동의로 시작한다.
     *
     * <p>간편인증은 이름·생년월일·휴대폰을 받는 개인정보 수집이라 <b>동의가 먼저</b>다.
     */
    public String startSignUp(List<AgreementChoice> choices) {
        requireSignUpAvailable();
        Map<AgreementType, Boolean> agreements = normalizeChoices(choices);
        requireAllMandatoryAgreed(agreements);
        return tokenFactory.issuePendingToken(PendingSignUp.self().withAgreements(agreements));
    }

    /**
     * 소셜 가입 — 약관 동의로 끝난다. 본인인증을 받지 않는다 (2026-09-21 결정).
     *
     * <p>만 14세 확인은 필수 약관 {@code AGE_14} 동의로 갈음한다 — 생년월일을 받지 않으므로 다른
     * 근거가 없다. 본인인증 공급자와 무관하게 열려 있다.
     *
     * @param signUpToken 소셜 콜백에서 받은 토큰
     */
    public SignInResponse signUpSocial(String signUpToken, List<AgreementChoice> choices,
                                       RequestInfo requestInfo, HttpServletResponse response) {
        PendingSignUp pending = tokenFactory.readPendingToken(signUpToken);
        if (!pending.isSocial()) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID, "소셜 가입 토큰이 아님");
        }
        Map<AgreementType, Boolean> agreements = normalizeChoices(choices);
        requireAllMandatoryAgreed(agreements);
        return accountService.createSocialAccount(pending.social(), agreements, requestInfo, response);
    }

    // =================================================================
    // 가입 2 — 간편인증 (자체 가입)
    // =================================================================

    /**
     * 본인확인 창을 여는 폼.
     *
     * <p>창을 열 때마다 새 거래번호를 만들어 진행 토큰에 넣는다. 돌아온 결과를 공급자에게
     * 되물었을 때 그 거래번호가 나와야 이 요청의 결과다.
     */
    public IdentityStartResponse startIdentityVerification(String signUpToken, IdentityMethod method) {
        PendingSignUp pending = tokenFactory.readPendingToken(signUpToken);
        if (pending.isSocial()) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID, "소셜 가입은 본인인증을 받지 않는다");
        }
        if (!pending.hasAgreements()) {
            throw new BusinessException(ErrorCode.AGREEMENT_REQUIRED, "약관 동의 전 본인확인 시도");
        }
        if (pending.isIdentityVerified()) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID, "이미 본인확인을 마친 토큰");
        }
        String requestId = tokenFactory.newIdentityRequestId();
        IdentityStartForm form = identityVerificationService.start(requestId, chosen(method));
        return new IdentityStartResponse(IdentityStartResponse.Form.from(form),
                tokenFactory.issuePendingToken(pending.withIdentityRequest(requestId)));
    }

    /**
     * 본인확인 공급자가 결과를 보낸 뒤 사용자를 보낼 화면 주소.
     *
     * <p>공급자는 결과를 브라우저 폼 POST 로 백엔드에 보낸다. 받은 값을 짧게 만료되는 토큰으로
     * 묶어 화면 주소에 붙인다 — 화면은 그 토큰을 진행 토큰과 함께 {@code /identity/complete} 에 낸다.
     * 여기서 신원을 조회하지 않는 이유는, 이 시점에는 <b>어느 흐름(가입·비밀번호 찾기)의 결과인지
     * 모르기</b> 때문이다. 그것은 화면이 들고 있는 진행 토큰이 안다.
     */
    public String identityReturnRedirect(Map<String, String> returned) {
        String token = tokenFactory.issueIdentityReturnToken(returned, IDENTITY_RETURN_KEYS);
        String page = appProperties.identity().returnPageUrl();
        return page + (page.contains("?") ? "&" : "?")
                + "providerToken=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    /**
     * 간편인증 결과를 받아 다음 갈 곳을 정한다 (자체 가입).
     *
     * <p>여기가 <b>기존 회원 판정 지점</b>이다. 비밀번호·이메일까지 다 받은 뒤에 "이미 회원이시네요"는
     * 나쁜 흐름이라, 판정 키를 손에 넣는 순간 판정한다.
     *
     * <p>같은 사람의 계정이 소셜로만 있어도 "이미 가입된 계정"으로 안내한다 — 그 계정에 비밀번호를
     * 정하면 이번 인증으로 번호가 채워져 휴대폰 로그인이 된다. 계정을 하나 더 만들지 않는다.
     */
    public IdentityResultResponse completeIdentityVerification(String signUpToken, String providerToken,
                                                               RequestInfo requestInfo) {
        PendingSignUp pending = tokenFactory.readPendingToken(signUpToken);
        if (pending.isSocial()) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID, "소셜 가입은 본인인증을 받지 않는다");
        }
        if (!pending.hasAgreements()) {
            throw new BusinessException(ErrorCode.AGREEMENT_REQUIRED, "약관 동의 전 본인인증 결과 제출");
        }

        // 외부 호출이 먼저다. 트랜잭션 안에서 부르면 공급자가 느릴 때 커넥션을 붙잡는다.
        IdentityVerification verification = identityVerificationService.verifyAndRecord(
                pending.identityRequestId(),
                tokenFactory.readIdentityReturnToken(providerToken),
                IdentityPurpose.SIGNUP,
                requestInfo);

        Optional<UserAccountService.ExistingAccount> existing =
                accountService.findExistingAndRefresh(verification.getId());

        if (existing.isPresent()) {
            UserAccountService.ExistingAccount account = existing.get();
            // 이미 본인인증을 통과했으므로 비밀번호 찾기를 따로 거치지 않고 바로 다시 정할 수 있다.
            log.info("[AUTH] 자체 가입 시도 — 이미 가입된 사람. userId={}", account.userId());
            return IdentityResultResponse.existing(
                    tokenFactory.issueResetToken(account.userId(), verification.getId()),
                    account.maskedEmail(), account.maskedPhone(), account.hasPassword());
        }

        return IdentityResultResponse.setPassword(
                tokenFactory.issuePendingToken(pending.withIdentity(verification.getId())),
                verification.getName(),
                PhoneNumber.normalizeOrNull(verification.getPhone()));
    }

    // =================================================================
    // 가입 3 — 비밀번호 · 이메일 (자체 가입)
    // =================================================================

    /**
     * 가입 중 선택 이메일로 인증번호를 보낸다.
     *
     * <p>본인확인을 마친 진행 토큰이 있어야 한다 — 아무나 우리 도메인으로 메일을 보내게 하지 않는다.
     * 이미 쓰이는 주소인지는 여기서 알려주지 않고, 가입을 마칠 때 판정한다.
     */
    public void sendSignUpEmailCode(String signUpToken, String email, RequestInfo requestInfo) {
        PendingSignUp pending = tokenFactory.readPendingToken(signUpToken);
        if (pending.isSocial() || !pending.isIdentityVerified()) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID, "이메일 인증 단계가 아님");
        }
        codeService.issue(VerificationChannel.EMAIL, normalizeEmail(email),
                VerificationPurpose.EMAIL_VERIFY, requestInfo, true);
    }

    /**
     * 자체 가입 완료.
     *
     * <p>인증번호 대조({@code confirm})는 자기 트랜잭션에서 시도 횟수를 커밋한다. 그 뒤 가입
     * 트랜잭션에서 그 번호를 소진한다.
     */
    public SignInResponse completeSignUp(SignUpCompleteRequest request, RequestInfo requestInfo,
                                         HttpServletResponse response) {
        PendingSignUp pending = tokenFactory.readPendingToken(request.signUpToken());
        if (pending.isSocial() || !pending.isIdentityVerified() || !pending.hasAgreements()) {
            throw new BusinessException(ErrorCode.SIGNUP_TOKEN_INVALID, "가입 완료 단계가 아님");
        }

        String email = request.email() == null || request.email().isBlank()
                ? null : normalizeEmail(request.email());
        Long emailCodeId = null;
        if (email != null) {
            String code = request.emailCode();
            if (code == null || code.isBlank()) {
                throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID, "이메일 인증번호 없음");
            }
            emailCodeId = codeService.confirm(VerificationChannel.EMAIL, email, code,
                            VerificationPurpose.EMAIL_VERIFY)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID));
        }

        return accountService.createVerifiedAccount(pending.identityVerificationId(), request.password(),
                email, emailCodeId, pending.agreements(), requestInfo, response);
    }

    // =================================================================
    // 휴대폰 로그인
    // =================================================================

    /**
     * 휴대폰번호 + 비밀번호.
     *
     * <p>실패 사유를 세분해 알려주지 않는다 — "번호가 없다"와 "비밀번호가 틀렸다"를 구분해 주면
     * 가입 여부를 확인하는 데 쓰인다. 형식이 틀린 번호도 "없는 번호"와 같은 응답이다. 다만
     * <b>비밀번호가 없는 계정</b>과 <b>잠긴 계정</b>은 알려준다 — 다음에 할 일이 다르다.
     */
    public SignInResponse signInWithPassword(String rawPhone, String rawPassword,
                                             HttpServletResponse response) {
        UserLoginAttemptService.Outcome outcome =
                loginAttemptService.attempt(PhoneNumber.normalizeOrNull(rawPhone), rawPassword);

        switch (outcome.result()) {
            case SUCCESS -> { }
            case NO_PASSWORD -> throw new BusinessException(ErrorCode.PASSWORD_NOT_SET);
            case LOCKED -> throw new BusinessException(ErrorCode.USER_ACCOUNT_LOCKED);
            case NOT_ALLOWED -> outcome.user().requireSignInAllowed();
            case BAD_CREDENTIALS -> throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }
        return accountService.completeSignIn(outcome.user().getId(), response);
    }

    /**
     * 인증번호 로그인 — 문자 또는 이메일로 보내기.
     *
     * <p><b>가입 여부를 알려주지 않는다.</b> 회원이든 아니든 같은 응답이다. 회원이 아니면 보내지
     * 않되 행은 남긴다 — 행을 남기지 않으면 쿨다운이 달라져 두 번째 요청에서 응답이 갈린다.
     * (문자는 비용까지 든다. 남의 번호로 문자를 보내게 두지 않는다.)
     */
    public void sendLoginCode(VerificationChannel channel, String rawTarget, RequestInfo requestInfo) {
        String target = requireSendable(channel, rawTarget);
        boolean member = findMember(channel, target)
                .filter(user -> user.getStatus().canSignIn())
                .isPresent();
        codeService.issue(channel, target, VerificationPurpose.LOGIN, requestInfo, member);
    }

    /** 인증번호 로그인 — 번호 확인. 성공 즉시 그 번호를 소진한다. */
    public SignInResponse signInWithCode(VerificationChannel channel, String rawTarget, String code,
                                         HttpServletResponse response) {
        String target = normalizeTarget(channel, rawTarget);
        Long codeId = codeService.confirm(channel, target, code, VerificationPurpose.LOGIN)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID));
        Long userId = findMember(channel, target)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID))
                .getId();
        codeService.consume(codeId);
        return accountService.completeSignIn(userId, response);
    }

    // =================================================================
    // 인증번호 대상 — 채널마다 다른 것을 여기서만 안다
    // =================================================================

    /**
     * 보낼 수 있는지 확인하고 대상을 정규화한다.
     *
     * <p>형식이 틀린 대상은 <b>여기서 막는다.</b> 그대로 두면 이상한 주소로 메일을 보내거나
     * 없는 번호로 문자를 보내 비용만 나간다.
     */
    private String requireSendable(VerificationChannel channel, String rawTarget) {
        if (!codeService.isAvailable(channel)) {
            throw new BusinessException(channel == VerificationChannel.SMS
                    ? ErrorCode.SMS_UNAVAILABLE : ErrorCode.MAIL_UNAVAILABLE);
        }
        return switch (channel) {
            case SMS -> PhoneNumber.normalize(rawTarget);
            case EMAIL -> requireEmail(rawTarget);
        };
    }

    /** 대조할 때 쓰는 정규화 — 형식이 틀리면 "번호가 틀렸다"와 같은 응답이다. */
    private String normalizeTarget(VerificationChannel channel, String rawTarget) {
        String target = switch (channel) {
            case SMS -> PhoneNumber.normalizeOrNull(rawTarget);
            case EMAIL -> rawTarget == null || rawTarget.isBlank() ? null : normalizeEmail(rawTarget);
        };
        if (target == null) {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID);
        }
        return target;
    }

    /**
     * 그 대상으로 로그인할 수 있는 회원.
     *
     * <p>이메일은 <b>인증을 마친 주소</b>여야 한다. 확인하지 않은 주소로 로그인을 허용하면,
     * 남의 주소를 적어 둔 계정으로 그 주소 주인이 들어가는 길이 된다.
     */
    private Optional<User> findMember(VerificationChannel channel, String target) {
        return switch (channel) {
            case SMS -> userRepository.findByPhone(target);
            case EMAIL -> userRepository.findByEmail(target)
                    .filter(user -> user.getEmailVerifiedAt() != null);
        };
    }

    private static String requireEmail(String raw) {
        String email = raw == null ? "" : raw.trim();
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일 형식이 올바르지 않습니다.");
        }
        return normalizeEmail(email);
    }

    // =================================================================
    // 비밀번호 찾기 — 본인확인 → 새 비밀번호
    // =================================================================

    /**
     * 비밀번호 찾기 간편인증 창을 여는 폼.
     *
     * <p>이메일 인증번호로 하지 않는다 — 이메일은 선택 항목이라 없는 회원이 있다. 비밀번호가 있는
     * 회원은 자체 가입 때 본인인증을 거쳤으므로 누구나 할 수 있다.
     */
    public IdentityStartResponse startPasswordReset(IdentityMethod method) {
        requireIdentityAvailable();
        String requestId = tokenFactory.newIdentityRequestId();
        IdentityStartForm form = identityVerificationService.start(requestId, chosen(method));
        return new IdentityStartResponse(IdentityStartResponse.Form.from(form),
                tokenFactory.issueResetFlowToken(requestId));
    }

    /**
     * 간편인증 결과로 계정을 찾아 재설정 권한(5분)을 준다.
     *
     * <p>계정 선택 화면이 없다 — 같은 사람의 계정이 여럿이어도(자체 + 본인인증한 소셜) 휴대폰 로그인이
     * 되는 쪽 하나를 고른다. 번호를 바꾼 회원이면 여기서 로그인 번호도 새 번호로 맞춰진다.
     * 본인인증을 한 번도 받지 않은 소셜 회원은 찾을 수 없다 — 소셜로 로그인하면 된다.
     */
    public PasswordResetGrantResponse completePasswordResetIdentity(String flowToken, String providerToken,
                                                                    RequestInfo requestInfo) {
        String requestId = tokenFactory.readResetFlowToken(flowToken);
        IdentityVerification verification = identityVerificationService.verifyAndRecord(
                requestId,
                tokenFactory.readIdentityReturnToken(providerToken),
                IdentityPurpose.PASSWORD_RESET,
                requestInfo);

        UserAccountService.ExistingAccount account = accountService
                .findExistingAndRefresh(verification.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTITY_NOT_REGISTERED));

        return new PasswordResetGrantResponse(
                tokenFactory.issueResetToken(account.userId(), verification.getId()),
                account.maskedEmail(), account.maskedPhone());
    }

    /**
     * 비밀번호 찾기 — 문자·이메일 인증번호 보내기.
     *
     * <p>본인인증(건당 과금)까지 가지 않아도 되는 대부분의 경우를 여기서 받는다. 가입 여부는
     * 알려주지 않는다 — 로그인 인증번호와 같은 규칙이다.
     *
     * <p>이 경로로 비밀번호를 바꿔도 <b>돈이 나가는 일은 막혀 있다.</b> 매입 신청·정산계좌 등록은
     * 비밀번호가 아니라 본인인증(STEP_UP)을 따로 요구한다.
     */
    public void sendPasswordResetCode(VerificationChannel channel, String rawTarget, RequestInfo requestInfo) {
        String target = requireSendable(channel, rawTarget);
        boolean member = findMember(channel, target)
                .filter(user -> user.getStatus().canSignIn())
                .isPresent();
        codeService.issue(channel, target, VerificationPurpose.PASSWORD_RESET, requestInfo, member);
    }

    /**
     * 비밀번호 찾기 — 인증번호 확인. 통과하면 재설정 권한(5분)을 준다.
     *
     * <p>인증번호는 여기서 소진하지 않는다. 재설정을 마칠 때 소진해야 토큰이 <b>한 번만</b> 쓰인다.
     */
    public PasswordResetGrantResponse completePasswordResetCode(VerificationChannel channel, String rawTarget,
                                                                String code) {
        String target = normalizeTarget(channel, rawTarget);
        Long codeId = codeService.confirm(channel, target, code, VerificationPurpose.PASSWORD_RESET)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID));
        var user = findMember(channel, target)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID));
        user.requireSignInAllowed();

        return new PasswordResetGrantResponse(
                tokenFactory.issueCodeResetToken(user.getId(), codeId),
                user.getEmail() == null ? null : PrivacyMask.email(user.getEmail()),
                user.getPhone() == null ? null : PrivacyMask.phone(user.getPhone()));
    }

    public void resetPassword(String resetToken, String newPassword) {
        OAuthTokenFactory.ResetGrant grant = tokenFactory.readResetToken(resetToken);
        accountService.resetPassword(grant, newPassword);
    }

    // =================================================================
    // 로그인한 회원의 본인인증 — 매입 신청·정산계좌 앞
    // =================================================================

    /**
     * 로그인한 회원이 간편인증 창을 여는 폼.
     *
     * <p>돈이 나가는 일(매입 대금 지급·정산계좌 등록) 앞에서 실명을 확인한다. 소셜 가입자도 예외 없다 —
     * 이때 처음으로 신원이 채워진다. 매입 기능은 {@link IdentityVerificationService#requireRecentStepUp}
     * 로 "최근에 본인인증을 받았는가"를 확인한다.
     */
    public IdentityStartResponse startStepUp(Long userId, IdentityMethod method) {
        requireIdentityAvailable();
        String requestId = tokenFactory.newIdentityRequestId();
        IdentityStartForm form = identityVerificationService.start(requestId, chosen(method));
        return new IdentityStartResponse(IdentityStartResponse.Form.from(form),
                tokenFactory.issueStepUpFlowToken(userId, requestId));
    }

    /** 간편인증 결과를 로그인한 회원에게 반영한다. 다른 사람 명의면 거부한다. */
    public UserResponse completeStepUp(Long userId, String flowToken, String providerToken,
                                       RequestInfo requestInfo) {
        String requestId = tokenFactory.readStepUpFlowToken(flowToken, userId);
        IdentityVerification verification = identityVerificationService.verifyAndRecord(
                requestId,
                tokenFactory.readIdentityReturnToken(providerToken),
                IdentityPurpose.STEP_UP,
                requestInfo);
        return accountService.applyStepUpIdentity(userId, verification.getId());
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

    // =================================================================
    // 내부
    // =================================================================

    /** 자체 가입은 본인인증 없이 성립하지 않는다. 공급자가 없으면 자체 가입이 닫힌다. */
    private void requireSignUpAvailable() {
        if (!identityVerificationService.isAvailable()) {
            throw new BusinessException(ErrorCode.SIGNUP_UNAVAILABLE);
        }
    }

    private void requireIdentityAvailable() {
        if (!identityVerificationService.isAvailable()) {
            throw new BusinessException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE);
        }
    }

    /**
     * 중복 선택을 정리한다. 같은 약관이 두 번 오면 <b>거부가 이긴다</b> —
     * 실수로 두 번 담겼을 때 동의로 처리하는 쪽이 훨씬 위험하다.
     */
    private Map<AgreementType, Boolean> normalizeChoices(List<AgreementChoice> choices) {
        Map<AgreementType, Boolean> map = new EnumMap<>(AgreementType.class);
        for (AgreementChoice choice : choices) {
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

    /** 화면이 고른 방식. 안 보냈으면 설정의 기본값(간편인증)을 쓴다. */
    private IdentityMethod chosen(IdentityMethod method) {
        return IdentityMethod.ofNullable(method, identityVerificationService.defaultMethod());
    }

    /** 소문자로 — 대소문자만 다른 주소로 쿨다운을 우회하거나 두 계정을 만들 수 없게 한다. */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
