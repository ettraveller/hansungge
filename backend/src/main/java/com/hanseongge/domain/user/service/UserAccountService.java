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
 * <h2>DI 가 사람, 휴대폰은 로그인 ID</h2>
 * "이미 가입한 사람인가"는 언제나 DI 로 판단한다. 휴대폰번호는 회선에 붙어 재할당되므로
 * 로그인 ID 로만 쓰고, 다른 DI 가 같은 번호로 본인확인을 통과하면 옛 계정의 번호를 비운다
 * ({@link #releasePhoneFromOthers}).
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
     * 본인확인한 사람이 이미 회원인가.
     *
     * <p>회원이면 그 회원의 신원을 이번 본인확인 값으로 맞춘다 — 번호를 바꿨거나 개명했을 수
     * 있다. 같은 DI 이므로 같은 사람이고, 확인 절차를 막 통과했다. 그 번호를 다른 계정이 쥐고
     * 있으면(재할당) 그쪽을 비운다.
     *
     * <p><b>로그인 수단을 붙이지는 않는다.</b> 소셜 계정을 연결할지는 사용자가 화면에서 확인한 뒤
     * {@link #linkSocial} 로 정한다 — 묻지 않고 붙이면 사용자가 모르는 사이 로그인 수단이 늘어난다.
     */
    @Transactional
    public Optional<ExistingAccount> findExistingAndRefresh(Long identityVerificationId) {
        IdentityVerification verification = findVerification(identityVerificationId);
        Optional<User> found = userRepository.findByDi(verification.getDi());
        if (found.isEmpty()) {
            return Optional.empty();
        }

        User user = found.get();
        user.requireSignInAllowed();
        releasePhoneFromOthers(PhoneNumber.normalize(verification.getPhone()), user.getId());
        user.refreshIdentity(verification.toIdentity(), verification.getCarrier());

        return Optional.of(new ExistingAccount(
                user.getId(),
                user.getEmail() == null ? null : PrivacyMask.email(user.getEmail()),
                user.getPhone() == null ? null : PrivacyMask.phone(user.getPhone()),
                user.hasPassword()));
    }

    /**
     * 기존 계정에 소셜 로그인을 연결하고 로그인시킨다 — 사용자가 연결 화면에서 [연결하기]를 눌렀다.
     *
     * <p>계정 병합이 아니다. 새 계정은 아직 없으므로 합칠 데이터가 없고, 소셜 연결 행 하나를
     * 추가할 뿐이다. 근거는 본인확인기관이 발급한 DI 다 — 이메일이 같다는 이유로 붙이지 않는다.
     */
    @Transactional
    public SignInResponse linkSocial(Long identityVerificationId, OAuthUserInfo social,
                                     HttpServletResponse response) {
        IdentityVerification verification = requireUsable(identityVerificationId, IdentityPurpose.SIGNUP);
        User user = userRepository.findByDi(verification.getDi())
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTITY_VERIFICATION_INVALID,
                        "연결할 기존 회원이 없음"));
        user.requireSignInAllowed();
        attachSocial(user, social);
        verification.consume(user.getId());
        log.info("[AUTH] 기존 회원에 소셜 연결. userId={} provider={}", user.getId(), social.provider());
        return issueSession(user, response);
    }

    // =================================================================
    // 가입
    // =================================================================

    /**
     * 가입 — 회원·본인확인 연결·소셜연동·동의이력을 <b>한 트랜잭션</b>에 만든다.
     *
     * <p>중간에 실패하면 아무것도 남지 않아야 한다: 동의 없는 계정도, 계정 없는 동의도
     * 만들지 않는다.
     */
    @Transactional
    public SignInResponse createAccount(SignUpMaterial material,
                                        Map<AgreementType, Boolean> choices,
                                        RequestInfo requestInfo,
                                        HttpServletResponse response) {
        IdentityVerification verification =
                requireUsable(material.identityVerificationId(), IdentityPurpose.SIGNUP);

        // 약관·비밀번호 화면에 머무는 사이 다른 탭에서 이미 가입했을 수 있다.
        if (userRepository.findByDi(verification.getDi()).isPresent()) {
            throw new BusinessException(ErrorCode.IDENTITY_ALREADY_REGISTERED);
        }

        String email = material.email();
        boolean emailVerified = material.isEmailVerified();
        if (email != null && userRepository.findByEmail(email).isPresent()) {
            if (material.isSocial() && material.emailCodeId() == null) {
                // 소셜 제공자가 준 주소를 다른 계정이 쓰고 있다. 이메일은 선택 항목이라
                // 가입을 막지 않고 비워 둔다 — 나중에 마이페이지에서 넣으면 된다.
                log.info("[AUTH] 소셜 이메일이 다른 계정에서 사용 중 — 이메일 없이 가입. provider={}",
                        material.social().provider());
                email = null;
                emailVerified = false;
            } else {
                // 직접 입력한 주소. 같은 DI 는 위에서 걸렀으니 다른 사람이다.
                throw new BusinessException(ErrorCode.EMAIL_OWNED_BY_ANOTHER);
            }
        }

        releasePhoneFromOthers(PhoneNumber.normalize(verification.getPhone()), null);

        String passwordHash = material.rawPassword() == null
                ? null
                : passwordEncoder.encode(material.rawPassword());
        User user = userRepository.save(User.signUp(
                email, emailVerified, verification.toIdentity(), verification.getCarrier(), passwordHash));

        if (material.isSocial()) {
            attachSocial(user, material.social());
        }
        verification.consume(user.getId());
        if (material.emailCodeId() != null) {
            codeRepository.findById(material.emailCodeId()).ifPresent(VerificationCode::consume);
        }
        recordAgreements(user.getId(), choices, requestInfo);

        log.info("[AUTH] 신규 가입 완료. userId={} 경로={}",
                user.getId(), material.isSocial() ? material.social().provider() : "PHONE");
        return issueSession(user, response);
    }

    // =================================================================
    // 비밀번호
    // =================================================================

    /**
     * 본인확인을 거친 비밀번호 재설정.
     *
     * <p>그 본인확인이 <b>이 회원의 DI</b> 인지 다시 확인한다 — 토큰에 담긴 두 id 가 서로 다른
     * 사람의 것이면 남의 비밀번호를 바꾸는 길이 된다. 본인확인은 여기서 소진되어 한 번만 쓰인다.
     *
     * <p><b>그 회원의 다른 세션을 전부 끊는다.</b> 비밀번호를 잊은 상황은 계정을 빼앗긴 상황과
     * 구분되지 않는다. 남의 기기에 열려 있던 세션이 재설정 뒤에도 살아 있으면 소용이 없다.
     */
    @Transactional
    public void resetPassword(Long userId, Long identityVerificationId, String rawPassword) {
        IdentityVerification verification = findVerification(identityVerificationId);
        if (!verification.isUsable(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID, "본인확인 만료·소진");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));
        if (!verification.getDi().equals(user.getDi())) {
            log.warn("[AUTH] 재설정 토큰의 본인확인과 회원이 다름. userId={}", userId);
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID, "본인확인·회원 불일치");
        }
        user.requireSignInAllowed();
        user.changePassword(passwordEncoder.encode(rawPassword));
        verification.consume(userId);
        refreshTokenService.revokeAll(userId);
        log.info("[AUTH] 본인확인으로 비밀번호 재설정. userId={}", userId);
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
     * 이 번호를 쥔 다른 계정이 있으면 번호를 비운다 — 번호가 재할당됐다.
     *
     * <p>다른 DI 가 이 번호로 본인확인을 통과했다는 것은 통신사가 번호를 다른 사람에게 넘겼다는
     * 뜻이다. 비우지 않으면 {@code ux_users_phone_active} 에 막혀 새 주인이 가입하지 못하고,
     * 옛 계정에는 남의 번호로 인증번호 로그인이 걸린다.
     *
     * <p>비운 뒤 곧바로 flush 한다 — 같은 트랜잭션에서 그 번호를 새 계정에 넣을 때, 쓰기 순서가
     * 뒤집히면 유일 인덱스에 걸린다.
     *
     * @param keepUserId 이 번호의 정당한 주인(같은 DI). 새 가입이면 null
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

    /** Access 토큰은 본문으로, Refresh 토큰은 HttpOnly 쿠키로 나간다. */
    private SignInResponse issueSession(User user, HttpServletResponse response) {
        refreshTokenService.issue(user.getId(), response);
        return SignInResponse.signedIn(
                jwtProvider.issueAccessToken(user.getId()),
                jwtProvider.accessTtlSeconds(),
                UserResponse.from(user));
    }
}
