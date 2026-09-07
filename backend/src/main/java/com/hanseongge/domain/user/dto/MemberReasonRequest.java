package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사유만 받는 회원 조치 요청 (잠금 해제 등).
 *
 * <p>잠금 해제는 되돌릴 수 없는 조치는 아니지만, <b>본인 확인을 거쳤다는 근거</b>가
 * 남아야 한다. 계정을 열어 준 뒤 그 계정에서 사고가 나면 "누가 왜 열었나"가 첫 질문이다.
 */
@Schema(name = "MemberReasonRequest", description = "사유를 받는 회원 조치 요청")
public record MemberReasonRequest(

        @Schema(description = "사유", example = "고객센터 유선 본인 확인 후 잠금 해제",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "사유를 입력해 주세요.")
        @Size(max = 200)
        String reason
) {
}
