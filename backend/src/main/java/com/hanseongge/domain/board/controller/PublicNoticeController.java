package com.hanseongge.domain.board.controller;

import com.hanseongge.domain.board.dto.NoticeDetailResponse;
import com.hanseongge.domain.board.dto.NoticeSummaryResponse;
import com.hanseongge.domain.board.service.NoticeService;
import com.hanseongge.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공지사항 (공개).
 *
 * <p>로그인 없이 읽는다. {@code SecurityConfig} 의 공개 GET 목록에 이미 등록되어 있다.
 */
@Tag(name = "공지사항", description = "공개 공지 목록/상세")
@RestController
@RequestMapping("/api/v1/notices")
@RequiredArgsConstructor
public class PublicNoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "공지 목록",
               description = "노출 중인 공지만 반환한다. 고정 공지가 목록 맨 위에 온다.")
    @GetMapping
    public ResponseEntity<PageResponse<NoticeSummaryResponse>> list(
            @PageableDefault(size = 15) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(
                noticeService.findPublicPage(pageable), NoticeSummaryResponse::from));
    }

    /**
     * 공지 상세.
     *
     * <p>조회수를 먼저 올리고 본문을 읽는다. 순서를 바꾸면 방금 올린 값이 반영되지 않은
     * 본문이 나가서, 새로고침할 때마다 조회수가 한 박자씩 늦게 보인다.
     */
    @Operation(summary = "공지 상세", description = "조회 시 조회수가 1 증가한다.")
    @GetMapping("/{id}")
    public ResponseEntity<NoticeDetailResponse> detail(@PathVariable Long id) {
        noticeService.increaseViewCount(id);
        return ResponseEntity.ok(NoticeDetailResponse.from(noticeService.findPublic(id)));
    }
}
