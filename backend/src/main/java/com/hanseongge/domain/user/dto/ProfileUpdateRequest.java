package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "ProfileUpdateRequest", description = "내 정보 수정")
public record ProfileUpdateRequest(

        @Schema(description = "이름", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 100)
        String name,

        @Schema(description = "연락처", example = "010-1234-5678")
        @Size(max = 30)
        // 빈 문자열도 허용한다 — 연락처를 지우는 것이 정상 동작이다.
        @Pattern(regexp = "^$|^0\\d{1,2}-?\\d{3,4}-?\\d{4}$",
                message = "연락처 형식이 올바르지 않습니다.")
        String phone
) {}
