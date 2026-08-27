package com.hanseongge.global.health;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 헬스체크 응답 DTO.
 *
 * <p>이 프로젝트에서 Entity 는 절대 API 로 나가지 않는다. 모든 응답은 이런 record DTO 다.
 */
@Schema(name = "HealthResponse", description = "서비스 상태")
public record HealthResponse(
        @Schema(description = "서비스명", example = "hanseongge-api")
        String service,

        @Schema(description = "애플리케이션 버전", example = "0.1.0")
        String version,

        @Schema(description = "활성 프로파일", example = "local")
        String profile,

        @Schema(description = "DB 연결 및 Flyway 적용 상태", example = "UP")
        String database,

        @Schema(description = "적용된 마지막 마이그레이션 버전", example = "7")
        String schemaVersion,

        @Schema(description = "적용 중인 반올림 정책 (TODO A-2 미확정 시 임시값)", example = "WON_FLOOR")
        String roundingPolicy,

        @Schema(description = "반올림 정책이 A-2 확정 전 임시값인지", example = "true")
        boolean roundingPolicyTemporary,

        @Schema(description = "적용 중인 부가세 정책 (TODO A-1 미확정 시 임시값)", example = "NONE")
        String vatMode,

        @Schema(description = "부가세 정책이 A-1 확정 전 임시값인지", example = "true")
        boolean vatPolicyTemporary,

        @Schema(description = "서버 시각 (KST)")
        OffsetDateTime serverTime
) {}
