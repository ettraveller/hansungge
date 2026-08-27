package com.hanseongge.domain.audit.dto;

import com.hanseongge.domain.audit.entity.ActorType;
import com.hanseongge.domain.audit.entity.AuditLog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 감사로그 조회 응답. Entity 를 그대로 내보내지 않는다 (코딩규칙 P4).
 */
@Schema(name = "AuditLogResponse", description = "감사로그 1건")
public record AuditLogResponse(

        @Schema(description = "로그 ID", example = "1042")
        Long id,

        @Schema(description = "행위 주체 구분", example = "ADMIN")
        ActorType actorType,

        @Schema(description = "행위자 (관리자 login_id 또는 SYSTEM)", example = "admin")
        String actor,

        @Schema(description = "행위", example = "ADMIN_LOGIN")
        String action,

        @Schema(description = "대상 유형", example = "ADMIN_USER")
        String targetType,

        @Schema(description = "대상 식별자", example = "3")
        String targetId,

        @Schema(description = "변경 전 값 (JSON 문자열)")
        String beforeValue,

        @Schema(description = "변경 후 값 (JSON 문자열)")
        String afterValue,

        @Schema(description = "요청 IP", example = "192.0.2.10")
        String ip,

        @Schema(description = "User-Agent")
        String userAgent,

        @Schema(description = "기록 시각", example = "2026-08-24T10:11:37+09:00")
        OffsetDateTime createdAt
) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorType(),
                log.getActor(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getBeforeValue(),
                log.getAfterValue(),
                log.getIp(),
                log.getUserAgent(),
                log.getCreatedAt());
    }
}
