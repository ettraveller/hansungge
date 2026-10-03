package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.dto.AgreementStatusResponse;
import com.hanseongge.domain.user.dto.SocialAccountResponse;
import com.hanseongge.domain.user.dto.UserResponse;
import com.hanseongge.domain.user.entity.AgreementType;
import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.domain.user.entity.SocialAccount;
import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.entity.UserAgreement;
import com.hanseongge.domain.user.entity.VerificationChannel;
import com.hanseongge.domain.user.entity.VerificationPurpose;
import com.hanseongge.domain.user.repository.SocialAccountRepository;
import com.hanseongge.domain.user.repository.UserAgreementRepository;
import com.hanseongge.domain.user.repository.UserRepository;
import com.hanseongge.global.auth.RequestInfo;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;

/**
 * 마이페이지 — 내 정보·약관·소셜 연동·탈퇴.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final UserAgreementRepository agreementRepository;
    private final AgreementCatalog agreementCatalog;
    private final UserRefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService codeService;

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return UserResponse.from(find(userId));
    }

    /**
     * 비밀번호 설정·변경.
     *
     * <p>소셜로만 가입해 비밀번호가 없으면 새로 정한다. 있으면 <b>현재 비밀번호를 확인</b>한다 —
     * 열어 둔 기기에서 남이 바꿔 버리는 것을 막는다. 현재 비밀번호를 잊었으면 비밀번호 찾기
     * (본인확인)로 간다.
     *
     * <p>이름·휴대폰은 여기서 고칠 수 없다. 본인확인으로 확정된 값이라, 바꾸려면 본인확인을
     * 다시 받아야 한다 (비밀번호 찾기에서 본인확인하면 새 번호로 맞춰진다).
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = find(userId);
        boolean hadPassword = user.hasPassword();
        if (hadPassword) {
            if (currentPassword == null
                    || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
                throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
            }
            user.changePassword(passwordEncoder.encode(newPassword));
        } else {
            user.attachPassword(passwordEncoder.encode(newPassword));
        }
        log.info("[AUTH] 마이페이지 비밀번호 {}. userId={}", hadPassword ? "변경" : "설정", userId);
    }

    // =================================================================
    // 이메일
    // =================================================================

    /**
     * 새 주소로 인증번호를 보낸다.
     *
     * <p>가입 때 이메일을 건너뛴 회원(소셜 가입자 전부)이 나중에 주소를 넣는 경로다.
     *
     * <p><b>보내기 전에 중복을 본다.</b> 가입 흐름은 일부러 마지막에 판정하지만 —
     * 거기서는 "이 주소가 이미 가입돼 있다" 를 알려주는 것이 가입 여부 조회가 되기
     * 때문이다 — 여기는 이미 로그인한 회원이라 그 걱정이 없고, 인증번호를 다 받고 나서
     * 마지막에 거절당하는 것이 더 나쁘다.
     *
     * <p>트랜잭션을 걸지 않는다. 안에서 <b>메일을 보낸다</b> — 외부 HTTP 가 끝날 때까지
     * DB 연결을 붙잡고 있게 되고, 메일 서버가 느리면 연결 풀이 먼저 마른다.
     * 인증번호 저장은 {@code codeService.issue} 가 자기 트랜잭션에서 한다.
     */
    public void sendEmailCode(Long userId, String rawEmail, RequestInfo requestInfo) {
        User user = find(userId);
        String email = normalizeEmail(rawEmail);
        requireEmailFree(email, user);
        codeService.issue(VerificationChannel.EMAIL, email,
                VerificationPurpose.EMAIL_CHANGE, requestInfo, true);
    }

    /**
     * 인증번호를 확인하고 주소를 확정한다.
     *
     * <p>중복을 여기서 다시 본다. 인증번호를 받는 사이에 다른 사람이 같은 주소로
     * 가입할 수 있다 — 그 창이 좁다고 없는 것은 아니다. 최종 방어선은 DB 의 부분 UNIQUE
     * 인덱스({@code ux_users_email_active}) 지만, 거기까지 가면 사용자에게는 알 수 없는
     * 오류가 된다.
     */
    @Transactional
    public UserResponse changeEmail(Long userId, String rawEmail, String code) {
        User user = find(userId);
        String email = normalizeEmail(rawEmail);
        requireEmailFree(email, user);

        Long codeId = codeService.confirm(VerificationChannel.EMAIL, email, code,
                        VerificationPurpose.EMAIL_CHANGE)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID));
        codeService.consume(codeId);

        user.changeEmail(email);
        log.info("[AUTH] 마이페이지 이메일 변경. userId={}", userId);
        return UserResponse.from(user);
    }

    /** 남이 쓰고 있지 않은 주소인가. 자기가 이미 쓰는 주소는 통과시킨다 — 재인증일 수 있다. */
    private void requireEmailFree(String email, User me) {
        userRepository.findByEmail(email)
                .filter(owner -> !owner.getId().equals(me.getId()))
                .ifPresent(owner -> {
                    throw new BusinessException(ErrorCode.EMAIL_OWNED_BY_ANOTHER);
                });
    }

    /** 소문자로 — 대소문자만 다른 주소로 두 계정이 생기지 않게 한다. */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    // =================================================================
    // 약관
    // =================================================================

    /**
     * 약관별 현재 상태.
     *
     * <p>이력이 없는 약관도 목록에 넣는다 — 새 약관이 생겼는데 화면에서 아예 사라지면
     * 사용자가 동의할 기회를 얻지 못한다.
     */
    @Transactional(readOnly = true)
    public List<AgreementStatusResponse> agreements(Long userId) {
        Map<AgreementType, UserAgreement> latest = new EnumMap<>(AgreementType.class);
        agreementRepository.findLatestPerType(userId)
                .forEach(row -> latest.put(row.getAgreementType(), row));

        return java.util.Arrays.stream(AgreementType.values())
                .sorted((a, b) -> Boolean.compare(b.isRequired(), a.isRequired()))
                .map(type -> {
                    UserAgreement row = latest.get(type);
                    return new AgreementStatusResponse(
                            type,
                            type.displayName(),
                            type.isRequired(),
                            row != null && row.isAgreed(),
                            row != null ? row.getVersion() : agreementCatalog.versionOf(type),
                            row != null ? row.getAgreedAt() : null);
                })
                .toList();
    }

    /**
     * 선택 약관 동의/철회.
     *
     * <p>필수 약관은 철회할 수 없다 — 철회하면 서비스를 이용할 근거가 사라지므로, 그것은
     * 동의 철회가 아니라 <b>탈퇴</b>다. 사용자가 의도를 착각한 채 필수 동의를 끄고
     * "왜 주문이 안 되지"가 되는 상황을 만들지 않는다.
     *
     * <p>기존 행을 고치지 않고 새 행을 추가한다 (append-only, D-9).
     */
    @Transactional
    public List<AgreementStatusResponse> changeAgreement(Long userId, AgreementType type,
                                                         boolean agreed, RequestInfo requestInfo) {
        find(userId);   // 존재·상태 확인
        if (type.isRequired() && !agreed) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "필수 약관은 철회할 수 없습니다. 이용을 중단하려면 탈퇴해 주세요.");
        }
        agreementRepository.save(UserAgreement.record(
                userId, type, agreementCatalog.versionOf(type), agreed,
                requestInfo == null ? null : requestInfo.ip()));
        log.info("[AUTH] 약관 상태 변경. userId={} type={} agreed={}", userId, type, agreed);
        return agreements(userId);
    }

    // =================================================================
    // 소셜 연동
    // =================================================================

    @Transactional(readOnly = true)
    public List<SocialAccountResponse> socialAccounts(Long userId) {
        return socialAccountRepository.findByUserIdOrderByConnectedAtAsc(userId).stream()
                .map(SocialAccountResponse::from)
                .toList();
    }

    /**
     * 연동 해제.
     *
     * <p>휴대폰+비밀번호로 로그인할 수 없는 회원의 마지막 소셜은 끊을 수 없다 — 다 끊으면 <b>본인도
     * 다시 들어올 수 없는 계정</b>이 된다. 비밀번호만 있고 휴대폰번호가 없으면(본인인증 전 소셜 회원)
     * 로그인할 ID 가 없으므로 끊을 수 없다. 인증번호 로그인은 세지 않는다. 문자 설정(SENS)이 없으면
     * 그 문이 닫혀 있다.
     */
    @Transactional
    public List<SocialAccountResponse> disconnect(Long userId, OAuthProviderType provider) {
        User user = find(userId);
        if (!user.canPasswordLogin() && socialAccountRepository.countByUserId(userId) <= 1) {
            throw new BusinessException(ErrorCode.SOCIAL_LAST_CONNECTION);
        }
        List<SocialAccount> targets = socialAccountRepository
                .findByUserIdOrderByConnectedAtAsc(userId).stream()
                .filter(account -> account.getProvider() == provider)
                .toList();
        if (targets.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "연동되지 않은 제공자: " + provider);
        }
        socialAccountRepository.deleteAll(targets);
        log.info("[AUTH] 소셜 연동 해제. userId={} provider={}", userId, provider);
        return socialAccounts(userId);
    }

    /**
     * 이미 로그인한 회원에게 다른 소셜 계정을 붙인다 (설계서 §7.2 의 "수동 연동").
     *
     * <p>이 경로로만 계정이 합쳐진다. 이메일이 같다는 이유로 서버가 알아서 합치지 않는다.
     */
    @Transactional
    public List<SocialAccountResponse> link(Long userId, OAuthProviderType provider,
                                            String providerUserId, String email) {
        find(userId);
        socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .ifPresent(existing -> {
                    if (!existing.getUserId().equals(userId)) {
                        throw new BusinessException(ErrorCode.SOCIAL_ALREADY_LINKED,
                                "다른 회원에 연결된 소셜 계정");
                    }
                    throw new BusinessException(ErrorCode.SOCIAL_ALREADY_LINKED, "이미 연결된 소셜 계정");
                });
        socialAccountRepository.save(SocialAccount.connect(userId, provider, providerUserId, email));
        log.info("[AUTH] 소셜 연동 추가. userId={} provider={}", userId, provider);
        return socialAccounts(userId);
    }

    // =================================================================
    // 탈퇴
    // =================================================================

    /**
     * 탈퇴.
     *
     * <p>행을 지우지 않는다 — 주문·결제가 회원을 참조하고, 거래 기록은 법정 보존 대상이다.
     * 상태만 WITHDRAWN 으로 바꾸고 모든 기기의 세션을 끊는다. 개인정보 파기는 보존기간
     * 경과 후 별도 배치가 처리한다.
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = find(userId);
        user.withdraw();
        refreshTokenService.revokeAll(userId);
        log.info("[AUTH] 회원 탈퇴 처리. userId={}", userId);
    }

    private User find(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.requireSignInAllowed();
        return user;
    }
}
