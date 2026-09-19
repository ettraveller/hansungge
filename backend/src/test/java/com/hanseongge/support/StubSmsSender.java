package com.hanseongge.support;

import com.hanseongge.domain.notification.SmsSendException;
import com.hanseongge.domain.notification.SmsSender;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 테스트용 문자 대역 — 보내지 않고 붙잡아 둔다.
 *
 * <p>인증번호는 문자 본문에만 있다. DB 에는 해시만 남으므로, 이것이 없으면 인증번호 로그인을
 * 한 번도 끝까지 태울 수 없다.
 */
public class StubSmsSender implements SmsSender {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    public record Sent(String phone, String text) {}

    public final List<Sent> sent = new ArrayList<>();

    /** false 로 두면 "문자 설정 없음"(인증번호 로그인 꺼짐)을 검증할 수 있다. */
    public boolean configured = true;

    public SmsSendException nextFailure;

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public void send(String phone, String text) {
        if (nextFailure != null) {
            throw nextFailure;
        }
        sent.add(new Sent(phone, text));
    }

    /** 마지막 문자에서 6자리 인증번호를 뽑는다. */
    public String lastCode() {
        if (sent.isEmpty()) {
            throw new IllegalStateException("발송된 문자가 없습니다.");
        }
        Matcher matcher = CODE.matcher(sent.get(sent.size() - 1).text());
        if (!matcher.find()) {
            throw new IllegalStateException("문자 본문에 인증번호가 없습니다.");
        }
        return matcher.group(1);
    }

    public void reset() {
        sent.clear();
        configured = true;
        nextFailure = null;
    }
}
