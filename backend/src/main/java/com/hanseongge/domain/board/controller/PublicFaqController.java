package com.hanseongge.domain.board.controller;

import com.hanseongge.domain.board.dto.FaqResponse;
import com.hanseongge.domain.board.service.FaqService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "자주 묻는 질문")
@RestController
@RequestMapping("/api/v1/faqs")
@RequiredArgsConstructor
public class PublicFaqController {

    private final FaqService faqService;

    @Operation(summary = "자주 묻는 질문", description = "내건 것만, 정해진 차례로.")
    @GetMapping
    public ResponseEntity<List<FaqResponse>> list() {
        return ResponseEntity.ok(faqService.findActive().stream().map(FaqResponse::from).toList());
    }
}
