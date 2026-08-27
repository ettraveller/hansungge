package com.hanseongge.domain.admin.dto;

import com.hanseongge.domain.admin.entity.AdminRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 관리자 계정 생성 요청 (SUPER_ADMIN 전용). */
@Schema(name = "AdminCreateRequest", description = "관리자 계정 생성 요청")
public record AdminCreateRequest(

        @Schema(description = "로그인 아이디 (영문 소문자·숫자·밑줄, 4~60자)", example = "kim_manager",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "아이디를 입력해 주세요.")
        @Size(min = 4, max = 60, message = "아이디는 4자 이상 60자 이하여야 합니다.")
        @Pattern(regexp = "^[a-z0-9_]+$", message = "아이디는 영문 소문자·숫자·밑줄만 사용할 수 있습니다.")
        String loginId,

        @Schema(description = "초기 비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Pattern(regexp = AdminPasswordPolicy.PATTERN, message = AdminPasswordPolicy.MESSAGE)
        String password,

        @Schema(description = "이름", example = "김담당", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 100)
        String name,

        @Schema(description = "권한 등급", example = "MANAGER", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "권한 등급을 선택해 주세요.")
        AdminRole role
) {
}
