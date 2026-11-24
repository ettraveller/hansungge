package com.hanseongge.domain.board.controller;

import com.hanseongge.domain.board.dto.FaqReplaceRequest;
import com.hanseongge.domain.board.dto.FaqResponse;
import com.hanseongge.domain.board.service.FaqService;
import com.hanseongge.global.auth.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "관리자 - 자주 묻는 질문")
@RestController
@RequestMapping("/api/admin/faqs")
@RequiredArgsConstructor
public class AdminFaqController {

    private final FaqService faqService;

    @Operation(summary = "질문 목록", description = "꺼 둔 것까지 차례대로.")
    @PreAuthorize("hasRole('OPERATOR')")
    @GetMapping
    public ResponseEntity<List<FaqResponse>> list() {
        return ResponseEntity.ok(faqService.findAll().stream().map(FaqResponse::from).toList());
    }

    @Operation(summary = "질문 전체 교체", description = "보낸 것이 곧 전체다. 빈 배열이면 모두 내린다.")
    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping
    public ResponseEntity<List<FaqResponse>> replace(
            @Valid @RequestBody FaqReplaceRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(faqService.replace(request, actor.loginId()).stream()
                .map(FaqResponse::from).toList());
    }
}
