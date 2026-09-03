package com.hanseongge.domain.admin.dto;

import com.hanseongge.domain.admin.entity.AdminStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 관리자 계정 상태 변경 요청.
 *
 * <p>사유를 <b>필수</b>로 받는다. 계정 정지·잠금 해제는 나중에 반드시 "왜 그랬나"를
 * 되묻게 되는 조치이고, 그 답이 감사로그에 없으면 기록이 있으나 마나다.
 */
@Schema(name = "AdminStatusChangeRequest", description = "관리자 계정 상태 변경 요청")
public record AdminStatusChangeRequest(

        @Schema(description = "변경할 상태", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "변경할 상태를 선택해 주세요.")
        AdminStatus status,

        @Schema(description = "변경 사유", example = "본인 확인 후 잠금 해제",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "변경 사유를 입력해 주세요.")
        @Size(max = 200)
        String reason
) {
}
