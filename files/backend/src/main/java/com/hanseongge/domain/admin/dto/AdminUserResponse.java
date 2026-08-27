package com.hanseongge.domain.admin.dto;

import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminStatus;
import com.hanseongge.domain.admin.entity.AdminUser;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 관리자 계정 응답.
 *
 * <p><b>{@code passwordHash} 는 어떤 경우에도 포함하지 않는다.</b> Entity 를 그대로
 * 반환하지 않는 이유가 바로 이것이다 (코딩규칙 P4). 필드를 추가할 때도 이 원칙을 지킨다.
 */
@Schema(name = "AdminUserResponse", description = "관리자 계정")
public record AdminUserResponse(

        @Schema(description = "계정 ID", example = "1")
        Long id,

        @Schema(description = "로그인 아이디", example = "admin")
        String loginId,

        @Schema(description = "이름", example = "최고 관리자")
        String name,

        @Schema(description = "권한 등급", example = "SUPER_ADMIN")
        AdminRole role,

        @Schema(description = "계정 상태", example = "ACTIVE")
        AdminStatus status,

        @Schema(description = "연속 로그인 실패 횟수", example = "0")
        int loginFailCount,

        @Schema(description = "마지막 로그인 시각")
        OffsetDateTime lastLoginAt,

        @Schema(description = "잠금 시각 (잠긴 계정만)")
        OffsetDateTime lockedAt,

        @Schema(description = "마지막 비밀번호 변경 시각")
        OffsetDateTime passwordChangedAt,

        @Schema(description = "생성 시각")
        OffsetDateTime createdAt
) {

    public static AdminUserResponse from(AdminUser admin) {
        return new AdminUserResponse(
                admin.getId(),
                admin.getLoginId(),
                admin.getName(),
                admin.getRole(),
                admin.getStatus(),
                admin.getLoginFailCount(),
                admin.getLastLoginAt(),
                admin.getLockedAt(),
                admin.getPasswordChangedAt(),
                admin.getCreatedAt());
    }
}
