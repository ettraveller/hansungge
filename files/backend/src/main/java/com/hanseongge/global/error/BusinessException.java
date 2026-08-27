package com.hanseongge.global.error;

import lombok.Getter;

import java.util.Map;

/**
 * 비즈니스 규칙 위반 예외.
 *
 * <p>기술적 장애(DB 끊김 등)에는 쓰지 않는다. 그쪽은 그대로 던져서
 * {@link GlobalExceptionHandler} 가 500 으로 처리하고 스택트레이스를 로그에 남기게 둔다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /** 항목별 오류. 없으면 null 이며, 응답의 errors 필드도 생략된다. */
    private final Map<String, String> fieldErrors;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.fieldErrors = null;
    }

    /** 로그에 남길 상세 사유를 별도로 전달한다. 사용자에게는 errorCode 의 메시지가 나간다. */
    public BusinessException(ErrorCode errorCode, String detailForLog) {
        super(detailForLog);
        this.errorCode = errorCode;
        this.fieldErrors = null;
    }

    /**
     * 항목별 사유를 함께 전달한다 ({@code errors} 필드로 나간다).
     *
     * <p>"필수 약관에 동의해야 합니다" 만으로는 사용자가 <b>무엇을</b> 눌러야 하는지 모른다.
     * @Valid 검증이 아닌 도메인 규칙에서도 항목을 짚어줘야 하는 경우가 있어 열어 둔다.
     */
    public BusinessException(ErrorCode errorCode, String detailForLog,
                             Map<String, String> fieldErrors) {
        super(detailForLog);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors == null || fieldErrors.isEmpty()
                ? null
                : Map.copyOf(fieldErrors);
    }
}
