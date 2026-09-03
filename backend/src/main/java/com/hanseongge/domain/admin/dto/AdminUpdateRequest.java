package com.hanseongge.domain.admin.dto;

import com.hanseongge.domain.admin.entity.AdminRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 관리자 계정 수정 요청 (이름·권한).
 *
 * <p>비밀번호와 상태는 여기에 넣지 않는다. 세 가지를 한 엔드포인트에서 받으면
 * "이름만 고치려던 요청"이 권한까지 덮어쓰는 사고가 난다. 각각 전용 엔드포인트를 둔다.
 */
@Schema(name = "AdminUpdateRequest", description = "관리자 계정 수정 요청")
public record AdminUpdateRequest(

        @Schema(description = "이름", example = "김담당", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 100)
        String name,

        @Schema(description = "권한 등급", example = "MANAGER", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "권한 등급을 선택해 주세요.")
        AdminRole role
) {
}
