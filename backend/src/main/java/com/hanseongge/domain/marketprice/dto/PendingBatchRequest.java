package com.hanseongge.domain.marketprice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 승인 대기 여러 건을 한 번에 처리하는 요청.
 *
 * <p>보통 한 금속의 살 때·팔 때 두 건이다. 한 건씩 처리하면 그 사이에 한쪽만 새 값인
 * 시세표가 화면에 걸린다.
 */
@Schema(name = "PendingBatchRequest", description = "승인 대기 일괄 처리")
public record PendingBatchRequest(

        @Schema(description = "대상 id 목록", example = "[12, 13]",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "처리할 건을 선택해 주세요.")
        @Size(max = 20, message = "한 번에 20건까지 처리할 수 있습니다.")
        List<Long> ids,

        @Schema(description = "사유", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "사유를 입력해 주세요.")
        @Size(max = 300)
        String reason
) {
}
