package com.hanseongge.domain.board.controller;

import com.hanseongge.domain.board.dto.AdminQnaResponse;
import com.hanseongge.domain.board.dto.QnaAnswerRequest;
import com.hanseongge.domain.board.entity.Qna;
import com.hanseongge.domain.board.entity.QnaBoardType;
import com.hanseongge.domain.board.service.QnaService;
import com.hanseongge.global.auth.AdminPrincipal;
import com.hanseongge.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 문의 관리 — 답변과 내리기. */
@Tag(name = "관리자 문의", description = "문의 답변 · 내리기")
@RestController
@RequestMapping("/api/admin/qnas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATOR')")
public class AdminQnaController {

    private final QnaService qnaService;

    @Operation(summary = "문의 목록",
               description = "내린 글도 보인다. unansweredOnly=true 면 미답변만 걸러 준다.")
    @GetMapping
    public ResponseEntity<PageResponse<AdminQnaResponse>> list(
            @RequestParam(required = false) QnaBoardType boardType,
            @RequestParam(defaultValue = "false") boolean unansweredOnly,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<Qna> page = qnaService.findAdminPage(boardType, unansweredOnly, pageable);
        List<Qna> rows = page.getContent();
        Map<Long, String> names = qnaService.authorNames(rows);
        Map<Long, String> products = qnaService.productNames(rows);

        return ResponseEntity.ok(PageResponse.of(page, qna -> AdminQnaResponse.of(
                qna,
                names.getOrDefault(qna.getUserId(), "-"),
                qna.getProductId() == null ? null : products.get(qna.getProductId()))));
    }

    @Operation(summary = "미답변 건수", description = "대시보드 배지용.")
    @GetMapping("/unanswered-count")
    public ResponseEntity<Map<String, Long>> unansweredCount() {
        return ResponseEntity.ok(Map.of("count", qnaService.countUnanswered()));
    }

    @Operation(summary = "문의 상세")
    @GetMapping("/{id}")
    public ResponseEntity<AdminQnaResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(qnaService.find(id)));
    }

    /**
     * 답변 등록·수정.
     *
     * <p>PUT 인 이유는 <b>답변이 문의당 하나</b>이기 때문이다. 여러 번 부르면 마지막 답변만
     * 남는다. 답변을 여러 개 달 수 있게 하려면 별도 테이블이 필요하고, 1:1 문의에는
     * 필요하지 않다고 판단했다.
     */
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "답변 등록/수정", description = "문의당 답변은 하나다. 다시 부르면 덮어쓴다.")
    @PutMapping("/{id}/answer")
    public ResponseEntity<AdminQnaResponse> answer(@PathVariable Long id,
                                                   @Valid @RequestBody QnaAnswerRequest request,
                                                   @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(toResponse(
                qnaService.answer(id, request.answer(), actor.loginId())));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "문의 내리기",
               description = "행을 삭제하지 않고 노출만 중단한다. 답변 여부와 무관하다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> hide(@PathVariable Long id) {
        qnaService.hide(id);
        return ResponseEntity.noContent().build();
    }

    private AdminQnaResponse toResponse(Qna qna) {
        List<Qna> one = List.of(qna);
        Map<Long, String> names = qnaService.authorNames(one);
        Map<Long, String> products = qnaService.productNames(one);

        return AdminQnaResponse.of(
                qna,
                names.getOrDefault(qna.getUserId(), "-"),
                qna.getProductId() == null ? null : products.get(qna.getProductId()));
    }
}
