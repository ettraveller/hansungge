package com.hanseongge.domain.notification;

/**
 * 메일 발송 실패.
 *
 * <p>서비스 계층이 {@code MAIL_SEND_FAILED} 로 바꿔 내보낸다. 공급자 응답 원문에는
 * 계정 정보가 섞여 나올 수 있어 사용자 응답에 싣지 않는다.
 */
public class MailSendException extends RuntimeException {

    public MailSendException(String message) {
        super(message);
    }

    public MailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
