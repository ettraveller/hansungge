package com.hanseongge.domain.board.controller;

import com.hanseongge.domain.board.dto.NoticeRequest;
import com.hanseongge.domain.board.dto.NoticeResponse;
import com.hanseongge.domain.board.service.NoticeService;
import com.hanseongge.global.auth.AdminPrincipal;
import com.hanseongge.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공지사항 관리.
 *
 * <p>클래스 레벨이 조회 최소 등급, 쓰기 메서드에 상위 등급을 덧씌운다 —
 * {@code AdminProductController} 와 같은 방식이다.
 */
@Tag(name = "관리자 공지", description = "공지사항 등록 · 수정 · 내리기")
@RestController
@RequestMapping("/api/admin/notices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATOR')")
public class AdminNoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "공지 목록", description = "내린 공지도 함께 보인다.")
    @GetMapping
    public ResponseEntity<PageResponse<NoticeResponse>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(
                noticeService.findAdminPage(pageable), NoticeResponse::from));
    }

    @Operation(summary = "공지 상세")
    @GetMapping("/{id}")
    public ResponseEntity<NoticeResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(NoticeResponse.from(noticeService.find(id)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "공지 등록")
    @PostMapping
    public ResponseEntity<NoticeResponse> create(@Valid @RequestBody NoticeRequest request,
                                                 @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(NoticeResponse.from(
                noticeService.create(request.title(), request.content(),
                        request.pinnedOrDefault(), actor.loginId())));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "공지 수정")
    @PutMapping("/{id}")
    public ResponseEntity<NoticeResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody NoticeRequest request) {
        return ResponseEntity.ok(NoticeResponse.from(
                noticeService.update(id, request.title(), request.content(),
                        request.pinnedOrDefault(), request.activeOrDefault())));
    }

    /**
     * 공지 내리기.
     *
     * <p>DELETE 지만 행을 지우지 않는다 — {@code active=false} 로 바꾼다. 상품에 DELETE 를
     * 두지 않은 것과 같은 판단이고, 공지는 "그때 그렇게 안내했다"는 근거로 남아야 한다.
     */
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "공지 내리기", description = "행을 삭제하지 않고 노출만 중단한다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> hide(@PathVariable Long id) {
        noticeService.hide(id);
        return ResponseEntity.noContent().build();
    }
}
