package com.hanseongge.domain.notification;

/**
 * 문자 발송 (설계서 원칙 P5 — 외부 연동은 인터페이스 뒤에 둔다).
 *
 * <p>{@link MailSender} 와 같은 구조다. 운영은 NCP SENS, 로컬·개발은 콘솔 출력.
 *
 * <p>{@link MailSender} 와 달리 {@link #isConfigured()} 가 있다 — 문자가 없으면 인증번호
 * 로그인 버튼 자체를 그리지 않아야 하기 때문이다. 메일은 가입 도중에 선택으로 쓰이지만,
 * 문자 로그인은 화면에 버튼으로 드러난다. 눌러야만 실패를 아는 버튼을 만들지 않는다.
 */
public interface SmsSender {

    /** 발신번호 등록·키 설정까지 끝나 실제로 보낼 수 있는가. */
    boolean isConfigured();

    /**
     * @param phone 숫자만 남긴 휴대폰번호
     * @param text  본문. 90바이트를 넘으면 공급자가 LMS 로 바꾸거나 거부한다 — 인증번호 문자는 짧게
     * @throws SmsSendException 설정 누락·공급자 장애
     */
    void send(String phone, String text);
}
