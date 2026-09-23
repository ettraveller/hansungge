package com.hanseongge.domain.notification;

/**
 * 메일 발송 (설계서 원칙 P5 — 외부 연동은 인터페이스 뒤에 둔다).
 *
 * <h2>왜 SMTP 가 아니라 인터페이스인가</h2>
 * {@code spring-boot-starter-mail} 을 쓰면 이 인터페이스 없이도 보낼 수는 있다. 그런데
 * SMTP 는 <b>실패가 잘 드러나지 않는다.</b> 자격증명이 틀려도 큐에 들어가 버리거나,
 * 포트가 막혀도 타임아웃으로만 나타난다. 운영에 얹는 설정(호스트·포트·TLS·계정)도
 * 그만큼 늘어난다.
 *
 * <p>NCP Cloud Outbound Mailer 는 REST API 를 제공하고, 실패가 HTTP 상태로 즉시 돌아온다.
 * 그리고 인터페이스 뒤에 두면 로컬 개발에서 <b>콘솔에 찍는 구현</b>으로 갈아 끼울 수 있다 —
 * 개발하면서 실제 메일을 자기 주소로 계속 받는 것은 검증이 아니라 방해다.
 */
public interface MailSender {

    /**
     * @param to      받는 주소
     * @param subject 제목
     * @param body    본문 (평문)
     * @throws MailSendException 발송 실패
     */
    void send(String to, String subject, String body);

    /**
     * 지금 실제로 보낼 수 있는가.
     *
     * <p>화면이 "이메일로 인증번호 받기"를 그릴지 정하는 데 쓴다 — 설정이 없는데 버튼을 그리면
     * 눌러야만 실패를 아는 화면이 된다. 개발용 구현(콘솔 출력)은 언제나 true 다.
     */
    boolean isConfigured();
}
