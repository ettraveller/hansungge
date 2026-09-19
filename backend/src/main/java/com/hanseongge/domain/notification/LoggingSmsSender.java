package com.hanseongge.domain.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 개발용 문자 발송 — 보내지 않고 콘솔에 찍는다.
 *
 * <p>{@link LoggingMailSender} 와 같은 이유로 있다. 이것이 없으면 로컬에서 인증번호 로그인을
 * 한 번도 해볼 수 없다.
 *
 * <p>⚠️ 인증번호가 서버 로그에 그대로 남는다. 그래서 개발 프로파일 전용이다 —
 * {@code prod} 에서는 빈이 만들어지지 않는다.
 */
@Slf4j
@Component
@Profile({"local", "dev"})
public class LoggingSmsSender implements SmsSender {

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public void send(String phone, String text) {
        log.info("""

                ┌── [SMS] 실제로 보내지 않았습니다 (개발용) ──
                │ 받는 번호 : {}
                ├──────────────────────────────────────────
                {}
                └──────────────────────────────────────────""", phone, text);
    }
}
