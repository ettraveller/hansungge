package com.hanseongge.support;

import com.hanseongge.domain.notification.MailSendException;
import com.hanseongge.domain.notification.MailSender;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 테스트용 메일 대역 — 보내지 않고 붙잡아 둔다.
 *
 * <p>인증번호는 메일 본문에만 있다. 테스트가 그것을 읽을 방법이 없으면 <b>가입 흐름을
 * 한 번도 끝까지 태울 수 없다.</b> DB 에는 해시만 남으므로 거기서도 못 얻는다.
 */
public class StubMailSender implements MailSender {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    public record Sent(String to, String subject, String body) {}

    public final List<Sent> sent = new ArrayList<>();

    /** 설정해 두면 발송이 실패한다 — 발송 실패를 성공으로 응답하지 않는지 검증할 때 쓴다. */
    public MailSendException nextFailure;

    /** false 로 두면 "메일 설정 없음"(이메일 인증번호 숨김)을 검증할 수 있다. */
    public boolean configured = true;

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (nextFailure != null) {
            throw nextFailure;
        }
        sent.add(new Sent(to, subject, body));
    }

    /** 마지막 메일에서 6자리 인증번호를 뽑는다. */
    public String lastCode() {
        if (sent.isEmpty()) {
            throw new IllegalStateException("발송된 메일이 없습니다.");
        }
        Matcher matcher = CODE.matcher(sent.get(sent.size() - 1).body());
        if (!matcher.find()) {
            throw new IllegalStateException("메일 본문에 인증번호가 없습니다.");
        }
        return matcher.group(1);
    }

    public void reset() {
        sent.clear();
        nextFailure = null;
        configured = true;
    }
}
