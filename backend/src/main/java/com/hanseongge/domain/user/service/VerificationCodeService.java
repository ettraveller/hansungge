package com.hanseongge.domain.user.service;

import com.hanseongge.domain.notification.MailSendException;
import com.hanseongge.domain.notification.MailSender;
import com.hanseongge.domain.notification.SmsSendException;
import com.hanseongge.domain.notification.SmsSender;
import com.hanseongge.domain.user.entity.VerificationChannel;
import com.hanseongge.domain.user.entity.VerificationCode;
import com.hanseongge.domain.user.entity.VerificationPurpose;
import com.hanseongge.domain.user.repository.VerificationCodeRepository;
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
 * 인증번호 발급·대조 — 메일(이메일 확인)과 문자(로그인)가 같은 규칙을 쓴다.
 *
 * <h2>규칙</h2>
 * <ul>
 *   <li>6자리, 원문 대신 SHA-256 만 저장
 *   <li>유효 5분 · 시도 5회 · 재발송 60초 · 성공 즉시 소진
 *   <li>문자는 번호당 시간당 5통 — 번호만 알면 누구나 요청할 수 있어서다
 * </ul>
 *
 * <h2>맞았는지 틀렸는지만 알려준다</h2>
 * "만료됐다"와 "번호가 틀렸다"를 구분해 주지 않는다. 구분해 주면 유효한 번호를 찾는
 * 데 쓰인다 — 만료 안 된 요청을 골라 거기에만 시도를 집중하면 된다.
 *
 * <h2>대상은 정규화된 값으로 받는다</h2>
 * 이메일은 소문자, 휴대폰은 숫자만({@code PhoneNumber}). 모양만 다른 같은 대상으로
 * 쿨다운과 시간당 한도를 우회할 수 없게 하기 위해서다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_BOUND = 1_000_000;

    private final VerificationCodeRepository repository;
    private final MailSender mailSender;
    private final SmsSender smsSender;
    private final AppProperties appProperties;

    private AppProperties.SignUp policy() {
        return appProperties.signUp();
    }

    public boolean isSmsAvailable() {
        return smsSender.isConfigured();
    }

    /**
     * 인증번호를 만들어 보낸다.
     *
     * @param deliver false 면 행만 남기고 보내지 않는다. 인증번호 로그인에서 <b>가입되지
     *                않은 번호</b>에 쓴다 — 문자를 보낼 이유가 없지만(비용·스팸), 행을 남기지
     *                않으면 쿨다운이 달라져 두 번 연속 요청했을 때 회원이면 429, 아니면 204 가
     *                되어 가입 여부가 새어 나간다.
     */
    @Transactional
    public void issue(VerificationChannel channel, String target, VerificationPurpose purpose,
                      RequestInfo requestInfo, boolean deliver) {
        OffsetDateTime now = OffsetDateTime.now();
        requireNotTooFrequent(channel, target, purpose, now);

        String code = generateCode();
        OffsetDateTime expiresAt = now.plusMinutes(policy().codeTtlMinutes());
        repository.save(VerificationCode.issue(channel, target, code, purpose, expiresAt,
                requestInfo == null ? null : requestInfo.ip()));

        if (!deliver) {
            return;
        }
        switch (channel) {
            case EMAIL -> sendMail(target, purpose, code);
            case SMS -> sendSms(target, code);
        }
    }

    /**
     * 재발송 쿨다운과 문자 시간당 한도.
     *
     * <p>쿨다운이 없으면 같은 곳으로 무한히 보낼 수 있다. 메일은 우리 도메인의 평판을
     * 떨어뜨려 정상 메일까지 스팸함으로 가게 만들고, 문자는 우리 발신번호가 남의 휴대폰을
     * 괴롭히는 도구가 된다. 시간당 한도는 분당 1회 쿨다운으로는 한 시간에 60통이 나가는
     * 것을 막는다.
     */
    private void requireNotTooFrequent(VerificationChannel channel, String target,
                                       VerificationPurpose purpose, OffsetDateTime now) {
        int cooldown = policy().codeResendCooldownSeconds();
        if (repository.countSince(target, channel, purpose, now.minusSeconds(cooldown)) > 0) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "인증번호는 " + cooldown + "초 후에 다시 받을 수 있습니다.");
        }
        if (channel == VerificationChannel.SMS
                && repository.countSince(target, channel, purpose, now.minusHours(1))
                   >= policy().smsHourlyLimit()) {
            log.warn("[AUTH] 문자 인증번호 시간당 한도 초과. purpose={}", purpose);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "인증번호 요청이 너무 많습니다. 1시간 후에 다시 시도해 주세요.");
        }
    }

    /**
     * 인증번호를 대조한다.
     *
     * <p>⚠️ 시도 횟수 증가와 예외를 <b>같은 트랜잭션에 두지 않는다.</b> 여기서 틀렸다고
     * 예외를 던지면 증가한 횟수가 함께 롤백되어 아무리 틀려도 시도 제한이 걸리지 않는다.
     * 그래서 성공 여부만 반환하고, 예외 변환은 호출자가 트랜잭션 밖에서 한다.
     *
     * <p>횟수를 이미 다 쓴 경우에만 여기서 던진다 — 그때는 증가시킨 것이 없다.
     *
     * @return 맞았으면 그 행의 id, 아니면 empty
     */
    @Transactional
    public Optional<Long> confirm(VerificationChannel channel, String target, String code,
                                  VerificationPurpose purpose) {
        OffsetDateTime now = OffsetDateTime.now();
        Optional<VerificationCode> found = repository.findLatest(target, channel, purpose);
        if (found.isEmpty()) {
            return Optional.empty();
        }

        VerificationCode verification = found.get();
        if (verification.isExpired(now) || verification.getConsumedAt() != null) {
            return Optional.empty();
        }
        if (!verification.hasAttemptsLeft(policy().codeMaxAttempts())) {
            throw new BusinessException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        }
        return verification.matches(code) ? Optional.of(verification.getId()) : Optional.empty();
    }

    /** 성공 즉시 소진한다. 한 번 통과한 번호는 다시 통하지 않는다. */
    @Transactional
    public void consume(Long id) {
        repository.findById(id).ifPresent(VerificationCode::consume);
    }

    /** 여섯 자리. 앞자리가 0 이어도 유지되도록 문자열로 만든다. */
    private String generateCode() {
        return String.format("%06d", RANDOM.nextInt(CODE_BOUND));
    }

    private void sendMail(String email, VerificationPurpose purpose, String code) {
        try {
            // 제목에 "[한성금거래소]" 를 넣지 않는다 — 발신자명이 이미 "한성금거래소" 라 메일함에 두 번 보인다.
            mailSender.send(email, "이메일 인증번호 안내", """
                    아래 인증번호를 입력해 주세요.

                        %s

                    유효 시간은 %d분입니다.
                    본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.

                    한성금거래소
                    """.formatted(code, policy().codeTtlMinutes()));
        } catch (MailSendException e) {
            // 발송에 실패했는데 성공으로 응답하면 사용자는 오지 않는 메일을 기다린다.
            log.warn("[AUTH] 인증 메일 발송 실패. purpose={}", purpose, e);
            throw new BusinessException(ErrorCode.MAIL_SEND_FAILED);
        }
    }

    /**
     * 문자는 짧게 — 90바이트(한글 45자)를 넘으면 LMS 로 바뀌어 단가가 오른다.
     *
     * <p>"타인에게 알려주지 마세요"를 넣는 이유: 인증번호 로그인을 노린 사기는 대부분
     * "방금 간 번호 불러주세요"로 시작한다.
     */
    private void sendSms(String phone, String code) {
        if (!smsSender.isConfigured()) {
            throw new BusinessException(ErrorCode.SMS_UNAVAILABLE);
        }
        try {
            smsSender.send(phone, "[한성금거래소] 인증번호 " + code + " 타인에게 알려주지 마세요.");
        } catch (SmsSendException e) {
            log.warn("[AUTH] 인증 문자 발송 실패", e);
            throw new BusinessException(ErrorCode.SMS_SEND_FAILED);
        }
    }
}
