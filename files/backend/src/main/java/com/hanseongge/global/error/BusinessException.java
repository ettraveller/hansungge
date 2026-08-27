package com.hanseongge.global.error;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반 예외.
 *
 * <p>기술적 장애(DB 끊김 등)에는 쓰지 않는다. 그쪽은 그대로 던져서
 * {@link GlobalExceptionHandler} 가 500 으로 처리하고 스택트레이스를 로그에 남기게 둔다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 로그에 남길 상세 사유를 별도로 전달한다. 사용자에게는 errorCode 의 메시지가 나간다. */
    public BusinessException(ErrorCode errorCode, String detailForLog) {
        super(detailForLog);
        this.errorCode = errorCode;
    }
}
