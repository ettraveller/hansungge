package com.hanseongge.domain.user.service;

import com.hanseongge.domain.notification.MailSendException;
import com.hanseongge.domain.notification.MailSender;
import com.hanseongge.domain.user.entity.EmailVerification;
import com.hanseongge.domain.user.entity.EmailVerificationPurpose;
import com.hanseongge.domain.user.repository.EmailVerificationRepository;
import com.hanseongge.global.auth.RequestInfo;
import com.hanseongge.global.config.AppProperties;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 이메일 인증번호 발급·대조.
 *
 * <h2>왜 이메일 인증까지 받는가</h2>
 * 휴대폰 본인확인이 신원을 보증하므로 이메일은 "그 사람인지"를 증명하지 않는다. 그래도
 * 받는 이유는 <b>이메일이 로그인 아이디이자 유일한 연락 수단</b>이기 때문이다. 오타가
 * 있으면 비밀번호를 잊는 순간 계정을 영영 잃고, 주문 안내도 가지 않는다.
 *
 * <h2>맞았는지 틀렸는지만 알려준다</h2>
 * "만료됐다"와 "번호가 틀렸다"를 구분해 주지 않는다. 구분해 주면 유효한 번호를 찾는
 * 데 쓰인다 — 만료 안 된 요청을 골라 거기에만 시도를 집중하면 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;

    private final EmailVerificationRepository repository;
    private final MailSender mailSender;
    private final AppProperties appProperties;

    private AppProperties.SignUp policy() {
        return appProperties.signUp();
    }

    /**
     * 인증번호를 만들어 메일로 보낸다.
     *
     * <p>이미 가입된 주소인지 여기서 판단하지 않는다. 그 정보를 알려주면 <b>가입 여부를
     * 확인하는 도구</b>가 된다 — 아무 주소나 넣어 "이미 가입됨"이 뜨는지 보면 회원 명단을
     * 훑을 수 있다. 중복 판정은 가입을 실제로 마치는 시점에 한다.
     */
    @Transactional
    public void issue(String email, EmailVerificationPurpose purpose, RequestInfo requestInfo) {
        OffsetDateTime now = OffsetDateTime.now();

        // 재발송 쿨다운 — 없으면 같은 주소로 메일을 무한히 보낼 수 있고, 그것은 우리
        // 도메인의 평판을 떨어뜨려 <b>정상 메일까지 스팸함으로</b> 가게 만든다.
        OffsetDateTime since = now.minusSeconds(policy().emailCodeResendCooldownSeconds());
        if (repository.existsRecent(email, purpose, since)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "인증번호는 " + policy().emailCodeResendCooldownSeconds() + "초 후에 다시 받을 수 있습니다.");
        }

        String code = generateCode();
        OffsetDateTime expiresAt = now.plusMinutes(policy().emailCodeTtlMinutes());
        repository.save(EmailVerification.issue(email, code, purpose, expiresAt,
                requestInfo == null ? null : requestInfo.ip()));

        try {
            mailSender.send(email, subjectOf(purpose), bodyOf(purpose, code));
        } catch (MailSendException e) {
            // 발송에 실패했는데 성공으로 응답하면 사용자는 오지 않는 메일을 기다린다.
            log.warn("[AUTH] 인증 메일 발송 실패. purpose={}", purpose, e);
            throw new BusinessException(ErrorCode.MAIL_SEND_FAILED);
        }
    }

    /**
     * 인증번호를 대조한다.
     *
     * <p>⚠️ 시도 횟수 증가와 예외를 <b>같은 트랜잭션에 두지 않는다.</b> 여기서 예외를 던지면
     * 증가한 횟수가 함께 롤백되어 아무리 틀려도 시도 제한이 걸리지 않는다
     * ({@code AdminLoginAttemptService} 가 같은 이유로 결과를 값으로 돌려준다).
     * 그래서 이 메서드는 성공 여부만 반환하고, 예외 변환은 {@link #requireVerified} 가 한다.
     */
    @Transactional
    public boolean confirm(String email, String code, EmailVerificationPurpose purpose) {
        OffsetDateTime now = OffsetDateTime.now();
        Optional<EmailVerification> found = repository.findLatest(email, purpose);
        if (found.isEmpty()) {
            return false;
        }

        EmailVerification verification = found.get();
        if (verification.isExpired(now) || verification.getConsumedAt() != null) {
            return false;
        }
        if (!verification.hasAttemptsLeft(policy().emailCodeMaxAttempts())) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        return verification.matches(code);
    }

    /**
     * 인증을 마친 상태인지 확인하고 그 행을 돌려준다.
     *
     * <p>가입을 실제로 진행하기 직전에 부른다 — 인증만 받아 두고 한참 뒤에 가입하는
     * 경로를 막기 위해 만료를 다시 본다.
     */
    @Transactional(readOnly = true)
    public EmailVerification requireVerified(Long id) {
        EmailVerification verification = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_VERIFICATION_INVALID));
        if (!verification.isVerifiedAndUnused(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }
        return verification;
    }

    @Transactional(readOnly = true)
    public Optional<EmailVerification> findLatestVerified(String email, EmailVerificationPurpose purpose) {
        return repository.findLatest(email, purpose)
                .filter(v -> v.isVerifiedAndUnused(OffsetDateTime.now()));
    }

    /** 여섯 자리. 앞자리가 0 이어도 유지되도록 문자열로 만든다. */
    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(CODE_BOUND));
    }

    private String subjectOf(EmailVerificationPurpose purpose) {
        return switch (purpose) {
            case SIGNUP -> "[한성금거래소] 회원가입 인증번호";
            case PASSWORD_RESET -> "[한성금거래소] 비밀번호 재설정 인증번호";
            case CHANGE_EMAIL -> "[한성금거래소] 이메일 변경 인증번호";
        };
    }

    private String bodyOf(EmailVerificationPurpose purpose, String code) {
        return """
                아래 인증번호를 입력해 주세요.

                    %s

                유효 시간은 %d분입니다.
                본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.

                한성금거래소
                """.formatted(code, policy().emailCodeTtlMinutes());
    }
}
