package com.hanseongge.domain.user.dto;

import com.hanseongge.domain.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 회원 상태 변경 요청 (관리자).
 *
 * <p>사유를 <b>필수</b>로 받는다. 이용정지는 나중에 반드시 "왜 그랬나"를 되묻게 되는
 * 조치이고, 그 답이 감사로그에 없으면 기록이 있으나 마나다 — 관리자 계정 정지와 같은
 * 판단이다.
 */
@Schema(name = "MemberStatusChangeRequest", description = "회원 상태 변경 요청")
public record MemberStatusChangeRequest(

        @Schema(description = "변경할 상태. WITHDRAWN 은 지정할 수 없다",
                example = "BLOCKED", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "변경할 상태를 선택해 주세요.")
        UserStatus status,

        @Schema(description = "변경 사유", example = "허위 주문 반복으로 이용정지",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "변경 사유를 입력해 주세요.")
        @Size(max = 200)
        String reason
) {
}
