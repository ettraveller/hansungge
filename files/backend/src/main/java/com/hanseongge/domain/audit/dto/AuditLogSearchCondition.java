package com.hanseongge.domain.audit.dto;

import com.hanseongge.domain.audit.entity.ActorType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;

/**
 * 감사로그 검색 조건. 모든 필드가 선택이며, 지정하지 않으면 해당 조건을 걸지 않는다.
 *
 * <p>기간은 {@code [from, to)} 반열림 구간으로 해석한다. 닫힌 구간으로 두면
 * "8월 24일까지"가 그날 00:00:00 까지가 되어 하루가 통째로 빠지는 사고가 난다.
 */
@Schema(name = "AuditLogSearchCondition", description = "감사로그 검색 조건")
public record AuditLogSearchCondition(

        @Schema(description = "행위자 (부분 일치)", example = "admin")
        String actor,

        @Schema(description = "행위 주체 구분", example = "ADMIN")
        ActorType actorType,

        @Schema(description = "행위 (정확 일치)", example = "ADMIN_LOGIN_FAILED")
        String action,

        @Schema(description = "대상 유형", example = "ADMIN_USER")
        String targetType,

        @Schema(description = "대상 식별자", example = "3")
        String targetId,

        @Schema(description = "기간 시작 (이상)", example = "2026-08-01T00:00:00+09:00")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime from,

        @Schema(description = "기간 끝 (미만)", example = "2026-09-01T00:00:00+09:00")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime to
) {

    public boolean hasActor() {
        return actor != null && !actor.isBlank();
    }

    public boolean hasAction() {
        return action != null && !action.isBlank();
    }

    public boolean hasTargetType() {
        return targetType != null && !targetType.isBlank();
    }

    public boolean hasTargetId() {
        return targetId != null && !targetId.isBlank();
    }
}
