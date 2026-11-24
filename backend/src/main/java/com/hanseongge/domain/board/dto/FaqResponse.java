package com.hanseongge.domain.board.dto;

import com.hanseongge.domain.board.entity.Faq;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "FaqResponse", description = "자주 묻는 질문")
public record FaqResponse(Long id, String category, String question, String answer, boolean active) {

    public static FaqResponse from(Faq faq) {
        return new FaqResponse(faq.getId(), faq.getCategory(), faq.getQuestion(), faq.getAnswer(),
                faq.isActive());
    }
}
