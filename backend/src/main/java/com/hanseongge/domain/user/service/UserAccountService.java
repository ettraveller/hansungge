package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.dto.SignInResponse;
import com.hanseongge.domain.user.dto.UserResponse;
import com.hanseongge.domain.user.entity.AgreementType;
import com.hanseongge.domain.user.entity.IdentityPurpose;
import com.hanseongge.domain.user.entity.IdentityVerification;
import com.hanseongge.domain.user.entity.SocialAccount;
import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.entity.UserAgreement;
import com.hanseongge.domain.user.entity.VerificationCode;
import com.hanseongge.domain.user.entity.VerificationPurpose;
import com.hanseongge.domain.user.oauth.OAuthUserInfo;
import com.hanseongge.domain.user.repository.IdentityVerificationRepository;
import com.hanseongge.domain.user.repository.SocialAccountRepository;
import com.hanseongge.domain.user.repository.UserAgreementRepository;
import com.hanseongge.domain.user.repository.UserRepository;
import com.hanseongge.domain.user.repository.VerificationCodeRepository;
import com.hanseongge.global.auth.RequestInfo;
import com.hanseongge.global.auth.UserJwtProvider;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import com.hanseongge.global.util.PhoneNumber;
import com.hanseongge.global.util.PrivacyMask;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 회원 계정의 <b>상태를 바꾸는</b> 부분.
 *
 * <h2>왜 {@link UserAuthService} 와 나눠 두는가</h2>
 * 소셜 로그인·본인확인 흐름 안에는 외부 HTTP 호출이 있다. 그 호출까지 하나의 트랜잭션에
 * 넣으면 상대가 느릴 때 DB 커넥션을 붙잡은 채 대기하게 되고, 커넥션 풀이 먼저 마른다.
 * 같은 클래스 안에서 {@code @Transactional} 메서드를 호출하면 프록시를 거치지 않아
 * 트랜잭션이 조용히 무시되므로 빈 자체를 분리한다.
 *
 * <h2>판정 키가 사람, 휴대폰은 로그인 ID</h2>
 * "이미 가입한 사람인가"는 판정 키(CI 해시)로 판단한다. 휴대폰번호는 회선에 붙어 재할당되므로
 * 로그인 ID 로만 쓰고, 다른 사람이 같은 번호로 본인인증을 통과하면 옛 계정의 번호를 비운다
 * ({@link #releasePhoneFromOthers}).
 *
 * <h2>소셜 가입은 본인인증이 없다</h2>
 * 소셜 가입자는 약관 동의만으로 계정이 생긴다({@link #createSocialAccount}). 신원은 나중에
 * 로그인한 채 본인인증({@link #applyStepUpIdentity})을 받을 때 채워진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final UserAgreementRepository agreementRepository;
    private final IdentityVerificationRepository identityRepository;
    private final VerificationCodeRepository codeRepository;
    private final AgreementCatalog agreementCatalog;
    private final UserJwtProvider jwtProvider;
    private final UserRefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    /** 본인확인한 사람이 이미 회원일 때, 화면에 보여줄 것. 가린 값만 담는다. */
    public record ExistingAccount(Long userId, String maskedEmail, String maskedPhone, boolean hasPassword) {
    }

    // =================================================================
    // 로그인
    // =================================================================

    /** 이미 연동된 소셜 계정으로 로그인. */
    @Transactional
    public SignInResponse signIn(Long socialAccountId, OAuthUserInfo info,
                                 HttpServletResponse response) {
        SocialAccount account = socialAccountRepository.findById(socialAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "소셜 연동 없음: " + socialAccountId));
        User user = userRepository.findById(account.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        "소셜 연동은 있으나 회원이 없음: " + account.getUserId()));
        user.requireSignInAllowed();
        // 카카오처럼 이메일 동의를 나중에 허용한 경우를 채운다. 이름은 본인확인이 정하므로 건드리지 않는다.
        user.syncFromProvider(info.email());
        return issueSession(user, response);
    }

    /**
     * 비밀번호 또는 인증번호를 통과한 뒤 세션 발급.
     *
     * <p>실패 흔적도 지운다. 인증번호 로그인은 휴대폰을 가진 본인임을 증명한 것이라,
     * 비밀번호 실패로 걸린 잠금을 그대로 둘 이유가 없다.
     */
    @Transactional
    public SignInResponse completeSignIn(Long userId, HttpServletResponse response) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.requireSignInAllowed();
        user.recordLogin();
        return issueSession(user, response);
    }

    // =================================================================
    // 본인확인 뒤
    // =================================================================

    /**
     * 본인인증한 사람이 이미 회원인가 (자체 가입 시도·비밀번호 찾기).
     *
     * <p>회원이면 그 회원의 신원을 이번 본인인증 값으로 맞춘다 — 번호를 바꿨거나 개명했을 수
     * 있다. 같은 판정 키이므로 같은 사람이고, 확인 절차를 막 통과했다. 그 번호를 다른 계정이 쥐고
     * 있으면(재할당) 그쪽을 비운다.
     *
     * <p>같은 사람의 계정이 여럿이면(자체 + 본인인증한 소셜) 휴대폰 로그인이 되는 쪽을 고른다
     * ({@code UserRepository.findActiveByIdentityKey} 의 정렬).
     */
    @Transactional
    public Optional<ExistingAccount> findExistingAndRefresh(Long identityVerificationId) {
        IdentityVerification verification = findVerification(identityVerificationId);
        Optional<User> found = userRepository.findByIdentityKey(verification.getIdentityKey());
        if (found.isEmpty()) {
            return Optional.empty();
        }

        User user = found.get();
        user.requireSignInAllowed();
        releasePhoneFromOthers(PhoneNumber.normalize(verification.getPhone()), user.getId());
        user.applyVerifiedIdentity(verification.toIdentity(), verification.getCarrier());

        return Optional.of(new ExistingAccount(
                user.getId(),
                user.getEmail() == null ? null : PrivacyMask.email(user.getEmail()),
                user.getPhone() == null ? null : PrivacyMask.phone(user.getPhone()),
                user.hasPassword()));
    }

    // =================================================================
    // 가입
    // =================================================================

    /**
     * 자체 가입 — 회원·본인인증 연결·동의이력을 <b>한 트랜잭션</b>에 만든다.
     *
     * <p>중간에 실패하면 아무것도 남지 않아야 한다: 동의 없는 계정도, 계정 없는 동의도
     * 만들지 않는다.
     *
     * @param email       선택. 인증번호를 통과한 주소
     * @param emailCodeId 이메일을 넣었을 때 통과한 인증번호 행. 소진 표시용
     */
    @Transactional
    public SignInResponse createVerifiedAccount(Long identityVerificationId, String rawPassword,
                                                String email, Long emailCodeId,
                                                Map<AgreementType, Boolean> choices,
                                                RequestInfo requestInfo,
                                                HttpServletResponse response) {
        IdentityVerification verification = requireUsable(identityVerificationId, IdentityPurpose.SIGNUP);

        // 약관·비밀번호 화면에 머무는 사이 다른 탭에서 이미 가입했을 수 있다. 판정 키에 UNIQUE 가
        // 없으므로(V23) 막는 곳은 여기뿐이다. 소셜 계정만 있는 사람은 막지 않는다(중복가입 허용) —
        // 휴대폰으로 로그인되는 계정이 이미 있을 때만 막는다.
        boolean hasPhoneAccount = userRepository.findByIdentityKey(verification.getIdentityKey())
                .filter(User::canPasswordLogin)
                .isPresent();
        if (hasPhoneAccount) {
            throw new BusinessException(ErrorCode.IDENTITY_ALREADY_REGISTERED);
        }
        if (email != null && userRepository.findByEmail(email).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_OWNED_BY_ANOTHER);
        }

        releasePhoneFromOthers(PhoneNumber.normalize(verification.getPhone()), null);

        User user = userRepository.save(User.signUpVerified(
                email, emailCodeId != null, verification.toIdentity(), verification.getCarrier(),
                passwordEncoder.encode(rawPassword)));

        verification.consume(user.getId());
        if (emailCodeId != null) {
            codeRepository.findById(emailCodeId).ifPresent(VerificationCode::consume);
        }
        recordAgreements(user.getId(), choices, requestInfo);

        log.info("[AUTH] 신규 가입 완료. userId={} 경로=PHONE", user.getId());
        return issueSession(user, response);
    }

    /**
     * 소셜 가입 — 본인인증 없이 약관 동의만으로.
     *
     * <p>판정 키·휴대폰·비밀번호가 없는 계정이 생긴다. 만 14세 확인은 {@code AGE_14} 동의로 갈음한다
     * (필수 약관이라 여기 오기 전에 확인됐다).
     */
    @Transactional
    public SignInResponse createSocialAccount(OAuthUserInfo social,
                                              Map<AgreementType, Boolean> choices,
                                              RequestInfo requestInfo,
                                              HttpServletResponse response) {
        String email = social.email();
        if (email != null && userRepository.findByEmail(email).isPresent()) {
            // 제공자가 준 주소를 다른 계정이 쓰고 있다. 이메일은 선택 항목이라 가입을 막지 않고
            // 비워 둔다 — 나중에 마이페이지에서 넣으면 된다. 이메일로 계정을 합치지 않는다.
            log.info("[AUTH] 소셜 이메일이 다른 계정에서 사용 중 — 이메일 없이 가입. provider={}", social.provider());
            email = null;
        }

        User user = userRepository.save(User.signUpSocial(email, blankToNull(social.name())));
        attachSocial(user, social);
        recordAgreements(user.getId(), choices, requestInfo);

        log.info("[AUTH] 신규 가입 완료. userId={} 경로={}", user.getId(), social.provider());
        return issueSession(user, response);
    }

    // =================================================================
    // 로그인한 회원의 본인인증 (매입·정산계좌 전)
    // =================================================================

    /**
     * 로그인한 회원에게 본인인증 결과를 반영한다.
     *
     * <p>소셜 가입자는 여기서 처음으로 판정 키·실명·생년월일·휴대폰이 채워지고, 휴대폰 로그인
     * ID 도 생긴다. 이미 신원이 있는 회원이 <b>다른 사람 명의</b>로 인증하면 거부한다
     * ({@code IDENTITY_MISMATCH}) — 예금주 대조의 근거가 바뀌면 안 된다.
     *
     * <p>같은 사람의 다른 계정이 있어도 합치지 않는다 (중복가입 허용). 그 계정이 이미 이 번호로
     * 로그인하고 있으면 번호는 그쪽에 둔다 — 옮기면 그 사람의 휴대폰 로그인이 끊긴다. 번호를 쥔
     * 계정이 <b>다른 사람</b>의 것이면 재할당이므로 비우고 이 계정으로 가져온다.
     */
    @Transactional
    public UserResponse applyStepUpIdentity(Long userId, Long identityVerificationId) {
        IdentityVerification verification = requireUsable(identityVerificationId, IdentityPurpose.STEP_UP);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.requireSignInAllowed();

        // 명의 대조를 먼저 — 다른 사람이면 남의 계정 번호를 비우기 전에 멈춰야 한다.
        if (user.getIdentityKey() != null && !user.getIdentityKey().equals(verification.getIdentityKey())) {
            log.warn("[AUTH] 로그인한 회원과 다른 명의의 본인인증. userId={}", userId);
            throw new BusinessException(ErrorCode.IDENTITY_MISMATCH);
        }

        String phone = PhoneNumber.normalize(verification.getPhone());
        boolean heldBySamePersonElsewhere = userRepository.findByPhone(phone)
                .filter(holder -> !holder.getId().equals(userId))
                .filter(holder -> verification.getIdentityKey().equals(holder.getIdentityKey()))
                .isPresent();
        if (heldBySamePersonElsewhere) {
            user.applyVerifiedIdentityKeepingPhone(verification.toIdentity(), verification.getCarrier());
        } else {
            releasePhoneFromOthers(phone, userId);
            user.applyVerifiedIdentity(verification.toIdentity(), verification.getCarrier());
        }
        verification.consume(userId);

        log.info("[AUTH] 회원 본인인증 완료. userId={}", userId);
        return UserResponse.from(user);
    }

    // =================================================================
    // 비밀번호
    // =================================================================

    /**
     * 본인인증을 거친 비밀번호 재설정.
     *
     * <p>그 본인인증이 <b>이 회원의 판정 키</b> 인지 다시 확인한다 — 토큰에 담긴 두 id 가 서로 다른
     * 사람의 것이면 남의 비밀번호를 바꾸는 길이 된다. 본인인증은 여기서 소진되어 한 번만 쓰인다.
     *
     * <p><b>그 회원의 다른 세션을 전부 끊는다.</b> 비밀번호를 잊은 상황은 계정을 빼앗긴 상황과
     * 구분되지 않는다. 남의 기기에 열려 있던 세션이 재설정 뒤에도 살아 있으면 소용이 없다.
     */
    @Transactional
    public void resetPassword(OAuthTokenFactory.ResetGrant grant, String rawPassword) {
        Long userId = grant.userId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        user.requireSignInAllowed();

        String how = grant.isByIdentity()
                ? consumeIdentity(grant.identityVerificationId(), user)
                : consumeCode(grant.verificationCodeId(), user);

        user.changePassword(passwordEncoder.encode(rawPassword));
        refreshTokenService.revokeAll(userId);
        log.info("[AUTH] 비밀번호 재설정. userId={} 경로={}", userId, how);
    }

    /** 본인인증을 근거로 온 경우 — 그 인증이 <b>이 회원의 것</b>인지 다시 확인하고 소진한다. */
    private String consumeIdentity(Long identityVerificationId, User user) {
        IdentityVerification verification = findVerification(identityVerificationId);
        if (!verification.isUsable(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID, "본인인증 만료·소진");
        }
        if (!verification.getIdentityKey().equals(user.getIdentityKey())) {
            log.warn("[AUTH] 재설정 토큰의 본인인증과 회원이 다름. userId={}", user.getId());
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID, "본인인증·회원 불일치");
        }
        verification.consume(user.getId());
        return "IDENTITY";
    }

    /**
     * 문자·이메일 인증번호를 근거로 온 경우.
     *
     * <p>그 인증번호가 <b>이 회원의 연락처로</b> 간 것인지 다시 확인한다. 토큰은 우리가 서명했지만,
     * 확인을 한 곳에만 두면 그 한 곳이 틀렸을 때 남의 비밀번호가 바뀐다.
     *
     * <p>여기서 소진한다 — 인증번호 확인 단계에서 소진하면 토큰을 여러 번 쓸 수 있다.
     */
    private String consumeCode(Long verificationCodeId, User user) {
        VerificationCode code = codeRepository.findById(verificationCodeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        if (code.getPurpose() != VerificationPurpose.PASSWORD_RESET
                || code.getConsumedAt() != null
                || code.isExpired(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID, "인증번호 만료·소진");
        }
        String target = switch (code.getChannel()) {
            case SMS -> user.getPhone();
            case EMAIL -> user.getEmail() == null ? null : user.getEmail().toLowerCase(Locale.ROOT);
        };
        if (target == null || !target.equals(code.getTarget())) {
            log.warn("[AUTH] 재설정 인증번호의 대상과 회원이 다름. userId={}", user.getId());
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID, "인증번호·회원 불일치");
        }
        code.consume();
        return code.getChannel().name();
    }

    /** 쿠키 회전 후 새 Access 토큰을 낸다. */
    @Transactional(readOnly = true)
    public SignInResponse describeSession(Long userId, String accessToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.requireSignInAllowed();
        return SignInResponse.signedIn(accessToken, jwtProvider.accessTtlSeconds(),
                UserResponse.from(user));
    }

    // =================================================================
    // 내부
    // =================================================================

    private IdentityVerification findVerification(Long id) {
        return identityRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTITY_VERIFICATION_INVALID));
    }

    /** 목적까지 대조한다 — 가입하려고 받은 인증으로 다른 일을 하는 경로를 막는다. */
    private IdentityVerification requireUsable(Long id, IdentityPurpose purpose) {
        IdentityVerification verification = findVerification(id);
        if (verification.getPurpose() != purpose || !verification.isUsable(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.IDENTITY_VERIFICATION_INVALID);
        }
        return verification;
    }

    /**
     * 이 번호를 쥔 다른 계정이 있으면 번호를 비운다.
     *
     * <p>대개 번호가 재할당된 경우다 — 다른 사람이 이 번호로 본인인증을 통과했다는 것은 통신사가
     * 번호를 넘겼다는 뜻이다. 같은 사람의 다른 계정(자체 + 소셜)일 때도 로그인 ID 는 하나에만 있어야
     * 해서 옮긴다. 비우지 않으면 {@code ux_users_phone_active} 에 막히고, 옛 계정에는 남의 번호로
     * 인증번호 로그인이 걸린다.
     *
     * <p>비운 뒤 곧바로 flush 한다 — 같은 트랜잭션에서 그 번호를 새 계정에 넣을 때, 쓰기 순서가
     * 뒤집히면 유일 인덱스에 걸린다.
     *
     * @param keepUserId 이 번호를 가질 계정. 새 가입이면 null
     */
    private void releasePhoneFromOthers(String phone, Long keepUserId) {
        userRepository.findByPhone(phone)
                .filter(holder -> !holder.getId().equals(keepUserId))
                .ifPresent(holder -> {
                    holder.releasePhone();
                    userRepository.flush();
                    log.warn("[AUTH] 휴대폰번호 재할당 — 이전 계정의 로그인 번호를 비웠습니다. "
                            + "releasedUserId={} newOwnerUserId={}", holder.getId(), keepUserId);
                });
    }

    private void attachSocial(User user, OAuthUserInfo info) {
        try {
            socialAccountRepository.saveAndFlush(SocialAccount.connect(
                    user.getId(), info.provider(), info.providerUserId(), info.email()));
        } catch (DataIntegrityViolationException e) {
            // ux_social 위반 — 그 소셜 계정이 이미 다른 회원에게 붙어 있다.
            // 삼키면 회원 행만 남은 고아 계정이 생기므로 트랜잭션째 되돌린다.
            log.warn("[AUTH] 소셜 연동 중복. provider={}", info.provider());
            throw new BusinessException(ErrorCode.SOCIAL_ALREADY_LINKED, "이미 연동된 소셜 계정");
        }
    }

    /**
     * 동의 내역 기록.
     *
     * <p>선택 약관을 아예 보내지 않았어도 {@code agreed=false} 로 남긴다. 기록이 없으면
     * 나중에 "물어봤는데 거절한 것"인지 "묻지 않은 것"인지 구분할 수 없다.
     */
    private void recordAgreements(Long userId, Map<AgreementType, Boolean> choices,
                                  RequestInfo requestInfo) {
        List<UserAgreement> rows = new ArrayList<>();
        for (AgreementType type : AgreementType.values()) {
            boolean agreed = Boolean.TRUE.equals(choices.get(type));
            rows.add(UserAgreement.record(userId, type, agreementCatalog.versionOf(type),
                    agreed, requestInfo == null ? null : requestInfo.ip()));
        }
        agreementRepository.saveAll(rows);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Access 토큰은 본문으로, Refresh 토큰은 HttpOnly 쿠키로 나간다. */
    private SignInResponse issueSession(User user, HttpServletResponse response) {
        refreshTokenService.issue(user.getId(), response);
        return SignInResponse.signedIn(
                jwtProvider.issueAccessToken(user.getId()),
                jwtProvider.accessTtlSeconds(),
                UserResponse.from(user));
    }
}
