package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.dto.SignInResponse;
import com.hanseongge.domain.user.dto.UserResponse;
import com.hanseongge.domain.user.entity.AgreementType;
import com.hanseongge.domain.user.entity.EmailVerification;
import com.hanseongge.domain.user.entity.IdentityVerification;
import com.hanseongge.domain.user.entity.SocialAccount;
import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.entity.UserAgreement;
import com.hanseongge.domain.user.entity.VerifiedIdentity;
import com.hanseongge.domain.user.oauth.OAuthUserInfo;
import com.hanseongge.domain.user.repository.EmailVerificationRepository;
import com.hanseongge.domain.user.repository.IdentityVerificationRepository;
import com.hanseongge.domain.user.repository.SocialAccountRepository;
import com.hanseongge.domain.user.repository.UserAgreementRepository;
import com.hanseongge.domain.user.repository.UserRepository;
import com.hanseongge.global.auth.RequestInfo;
import com.hanseongge.global.auth.UserJwtProvider;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 *
 * <p>그렇다고 같은 클래스 안에서 {@code @Transactional} 메서드를 호출하면 <b>프록시를
 * 거치지 않아 트랜잭션이 조용히 무시된다.</b> 그래서 빈 자체를 분리한다.
 *
 * <h2>DI 가 계정의 진짜 열쇠다</h2>
 * 이메일은 바꿀 수 있고 소셜 계정은 여러 개일 수 있지만, DI 는 사람에 붙는다.
 * "이미 가입한 사람인가"의 판단은 언제나 DI 로 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final UserAgreementRepository agreementRepository;
    private final IdentityVerificationRepository identityRepository;
    private final EmailVerificationRepository emailRepository;
    private final AgreementCatalog agreementCatalog;
    private final UserJwtProvider jwtProvider;
    private final UserRefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

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

    /** 비밀번호 로그인 성공 뒤 세션 발급. */
    @Transactional
    public SignInResponse signInWithPassword(Long userId, HttpServletResponse response) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.requireSignInAllowed();
        user.recordLogin();
        return issueSession(user, response);
    }

    /**
     * 이 DI 로 이미 가입한 회원이 있으면 <b>로그인 수단만 붙이고</b> 그 계정으로 들여보낸다.
     *
     * <p>가입을 새로 만들지 않는 이유는 사용자 결정이다 — 같은 사람이 구글로 가입했다가
     * 나중에 이메일로 가입을 시도하거나 네이버로 로그인하면, 계정이 갈라지는 대신 하나로
     * 모인다. 주문·장바구니 이력이 이어지고, "가입이 안 된다"는 문의가 사라진다.
     *
     * <p>이것이 §7.2("이메일 기반 자동 병합은 계정 탈취 경로")를 뒤집는 것이 아니다.
     * 그 원칙은 <b>이메일</b> 자동 병합을 막은 것이고, 여기서 병합의 근거는 본인확인기관이
     * 발급한 DI 다. 남의 DI 를 만들어 낼 수는 없다.
     *
     * @return 병합해서 로그인시켰으면 세션, 신규 가입 대상이면 {@link Optional#empty()}
     */
    @Transactional
    public Optional<SignInResponse> mergeIfExisting(Long identityVerificationId,
                                                    OAuthUserInfo social,
                                                    String rawPassword,
                                                    HttpServletResponse response) {
        IdentityVerification verification = identityRepository.findById(identityVerificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTITY_VERIFICATION_INVALID));

        Optional<User> found = userRepository.findByDi(verification.getDi());
        if (found.isEmpty()) {
            return Optional.empty();
        }

        User user = found.get();
        user.requireSignInAllowed();

        if (social != null) {
            attachSocial(user, social);
        }
        if (rawPassword != null) {
            if (user.hasPassword()) {
                // 이미 비밀번호가 있다. 여기서 덮어쓰면 본인확인만으로 남의 비밀번호를
                // 바꾸는 길이 된다 — 그건 재설정이고 별도 흐름이다.
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED,
                        "이미 가입된 회원입니다. 로그인하거나 비밀번호 찾기를 이용해 주세요.");
            }
            user.attachPassword(passwordEncoder.encode(rawPassword));
        }

        verification.consume(user.getId());
        log.info("[AUTH] 기존 회원에 로그인 수단 추가. userId={} social={} password={}",
                user.getId(), social != null, rawPassword != null);
        return Optional.of(issueSession(user, response));
    }

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
        IdentityVerification verification = identityRepository.findById(material.identityVerificationId())
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTITY_VERIFICATION_INVALID));
        VerifiedIdentity identity = verification.toIdentity();

        // 약관 화면에 머무는 사이 다른 탭에서 이미 가입했을 수 있다.
        if (userRepository.findByDi(identity.di()).isPresent()) {
            throw new BusinessException(ErrorCode.IDENTITY_ALREADY_REGISTERED);
        }

        // 이 시점에 이메일이 겹친다면 DI 가 다른 사람이다 — 위에서 DI 중복을 이미 걸렀다.
        if (material.email() != null && userRepository.findByEmail(material.email()).isPresent()) {
            log.warn("[AUTH] 다른 신원이 사용 중인 이메일로 가입 시도");
            throw new BusinessException(ErrorCode.EMAIL_OWNED_BY_ANOTHER);
        }

        String passwordHash = material.rawPassword() == null
                ? null
                : passwordEncoder.encode(material.rawPassword());

        User user = userRepository.save(User.signUp(material.email(), identity, passwordHash));

        if (material.isSocial()) {
            attachSocial(user, material.social());
        }

        verification.consume(user.getId());
        consumeEmailVerification(material.emailVerificationId());
        recordAgreements(user.getId(), choices, requestInfo);

        log.info("[AUTH] 신규 가입 완료. userId={} 경로={}",
                user.getId(), material.isSocial() ? material.social().provider() : "EMAIL");
        return issueSession(user, response);
    }

    /**
     * 비밀번호 재설정.
     *
     * <p><b>그 회원의 다른 세션을 전부 끊는다.</b> 비밀번호를 잊어 재설정하는 상황은
     * 계정을 빼앗겼을 가능성과 구분되지 않는다. 남의 기기에 열려 있던 세션이
     * 비밀번호를 바꾼 뒤에도 살아 있으면 재설정이 아무 소용이 없다.
     */
    @Transactional
    public void resetPassword(Long userId, String rawPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.changePassword(passwordEncoder.encode(rawPassword));
        refreshTokenService.revokeAll(userId);
        log.info("[AUTH] 비밀번호 재설정. userId={}", userId);
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

    private void consumeEmailVerification(Long emailVerificationId) {
        if (emailVerificationId == null) {
            return;
        }
        emailRepository.findById(emailVerificationId).ifPresent(EmailVerification::consume);
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
