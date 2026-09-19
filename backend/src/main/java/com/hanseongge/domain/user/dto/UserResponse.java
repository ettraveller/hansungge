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
        @Schema(description = "휴대폰번호(로그인 ID). 숫자만", example = "01012345678") String phone,
        @Schema(description = "상태", example = "ACTIVE") UserStatus status,
        @Schema(description = "가입 시각") OffsetDateTime createdAt,
        /**
         * 비밀번호가 있는가. 해시는 절대 내보내지 않고 있고 없고만 알린다 — 마이페이지가
         * "비밀번호 정하기"와 "비밀번호 바꾸기"를 가르고, 마지막 소셜 연결 해제를 막을지 정한다.
         */
        @Schema(description = "비밀번호가 설정돼 있는가", example = "true") boolean hasPassword
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(),
                user.getPhone(), user.getStatus(), user.getCreatedAt(), user.hasPassword());
    }
}
