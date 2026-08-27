package com.hanseongge.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 표준 에러 응답.
 *
 * <pre>
 * {
 *   "code": "PRICE_UNAVAILABLE",
 *   "message": "현재 시세를 확인할 수 없습니다. 시세문의 후 주문해 주세요.",
 *   "timestamp": "2026-08-21T14:00:00+09:00"
 * }
 * </pre>
 *
 * <p>{@code errors} 는 Bean Validation 실패 시에만 채워지며, 그 외에는 응답에서 생략된다.
 * 스택트레이스·SQL·테이블명 등 내부 정보는 어떤 경우에도 담지 않는다 (설계서 §15).
 */
@Schema(name = "ErrorResponse", description = "표준 에러 응답")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @Schema(description = "오류 코드", example = "PRICE_UNAVAILABLE")
        String code,

        @Schema(description = "사용자에게 보여줄 메시지", example = "현재 시세를 확인할 수 없습니다.")
        String message,

        @Schema(description = "발생 시각 (KST)", example = "2026-08-21T14:00:00+09:00")
        OffsetDateTime timestamp,

        @Schema(description = "필드별 검증 오류 (입력값 오류일 때만)")
        Map<String, String> errors
) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), OffsetDateTime.now(KST), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, Map<String, String> fieldErrors) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), OffsetDateTime.now(KST),
                fieldErrors == null || fieldErrors.isEmpty() ? null : fieldErrors);
    }
}
