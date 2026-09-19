package com.hanseongge.domain.notification;

/**
 * 문자 발송 실패. 서비스가 {@code SMS_SEND_FAILED} 로 바꿔 내보낸다 — 공급자 응답 원문에는
 * 계정 정보가 섞여 나오는 경우가 있어 사용자 응답에 싣지 않는다.
 */
public class SmsSendException extends RuntimeException {

    public SmsSendException(String message) {
        super(message);
    }

    public SmsSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
