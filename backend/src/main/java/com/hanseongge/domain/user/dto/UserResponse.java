package com.hanseongge.domain.user.dto;

import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(name = "UserResponse", description = "회원")
public record UserResponse(

        @Schema(description = "회원 ID", example = "1") Long id,
        @Schema(description = "이메일. 제공자가 주지 않았으면 null") String email,
        @Schema(description = "이름") String name,
        @Schema(description = "연락처") String phone,
        @Schema(description = "상태", example = "ACTIVE") UserStatus status,
        @Schema(description = "가입 시각") OffsetDateTime createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(),
                user.getPhone(), user.getStatus(), user.getCreatedAt());
    }
}
