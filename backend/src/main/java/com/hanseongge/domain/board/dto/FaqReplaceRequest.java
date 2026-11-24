package com.hanseongge.domain.board.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "FaqReplaceRequest", description = "자주 묻는 질문 전체 교체")
public record FaqReplaceRequest(
        @Schema(description = "질문 목록. 보낸 차례대로 나간다. 빈 배열이면 모두 내린다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "질문 목록이 필요합니다.")
        @Size(max = MAX_FAQS, message = "질문은 " + MAX_FAQS + "개까지 넣을 수 있습니다.")
        List<Item> faqs
) {

    public static final int MAX_FAQS = 100;

    @Schema(name = "FaqItem")
    public record Item(
            @Schema(description = "묶음 이름", example = "주문·결제") String category,
            String question,
            @Schema(description = "답. 글자 그대로 나간다(HTML 아님)") String answer,
            @Schema(description = "내걸었는가. 비우면 내건 것으로 본다") Boolean active
    ) {}
}
