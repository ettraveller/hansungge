package com.hanseongge.domain.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 개발용 메일 발송 — 보내지 않고 콘솔에 찍는다.
 *
 * <h2>왜 필요한가</h2>
 * 가입에 이메일 인증이 들어가면서, 이것이 없으면 로컬에서 <b>회원가입을 끝까지 할 수
 * 없다.</b> 실제 메일을 자기 주소로 계속 받는 것은 검증이 아니라 방해이기도 하다.
 *
 * <h2>운영에 뜰 수 없다</h2>
 * {@code @Profile({"local","dev"})} 라 {@code prod} 에서는 빈이 만들어지지 않는다.
 * 이것이 운영에 뜨면 <b>인증 메일이 아무에게도 가지 않는데 아무도 모르는</b> 상태가 된다.
 *
 * <p>⚠️ 인증번호가 서버 로그에 그대로 남는다. 그래서 개발 프로파일 전용이다.
 */
@Slf4j
@Component
@Profile({"local", "dev"})
public class LoggingMailSender implements MailSender {

    /** 콘솔에 찍기만 하므로 언제나 쓸 수 있다. */
    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public void send(String to, String subject, String body) {
        log.info("""

                ┌── [MAIL] 실제로 보내지 않았습니다 (개발용) ──
                │ 받는 사람 : {}
                │ 제목      : {}
                ├──────────────────────────────────────────
                {}
                └──────────────────────────────────────────""", to, subject, body);
    }
}
