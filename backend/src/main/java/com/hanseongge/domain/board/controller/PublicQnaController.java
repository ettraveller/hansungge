package com.hanseongge.domain.board.controller;

import com.hanseongge.domain.board.dto.QnaDetailResponse;
import com.hanseongge.domain.board.dto.QnaRequest;
import com.hanseongge.domain.board.dto.QnaSummaryResponse;
import com.hanseongge.domain.board.entity.Qna;
import com.hanseongge.domain.board.entity.QnaBoardType;
import com.hanseongge.domain.board.service.QnaService;
import com.hanseongge.global.auth.UserPrincipal;
import com.hanseongge.global.dto.PageResponse;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 문의게시판 · 제품문의 (공개 + 회원).
 *
 * <h2>목록은 로그인 없이 보인다</h2>
 * 레퍼런스와 같은 방식이다. 다른 사람의 문의와 답변이 보이면 <b>같은 질문을 또 하지
 * 않는다</b> — 게시판을 두는 실질적인 이유가 그것이다. 대신 작성자는 가리고, 비밀글은
 * 제목만 보인다.
 *
 * <h2>쓰기는 로그인이 필요하다</h2>
 * {@code SecurityConfig} 가 GET 만 공개하고 나머지 메서드는 인증을 요구한다.
 * 소셜 로그인 키가 아직 없어 지금은 실제로 글을 쓸 수 없지만, 로그인이 붙는 날
 * <b>이 코드는 그대로</b> 동작한다 — 회원 식별을 {@code principal.id()} 하나에만
 * 의존하기 때문이다.
 */
@Tag(name = "문의", description = "문의게시판 · 제품문의")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PublicQnaController {

    private final QnaService qnaService;

    @Operation(summary = "문의 목록",
               description = "게시판별 목록. 비밀글도 제목은 보이되 본문은 상세에서 막힌다.")
    @GetMapping("/qnas")
    public ResponseEntity<PageResponse<QnaSummaryResponse>> list(
            @RequestParam QnaBoardType boardType,
            @PageableDefault(size = 15) Pageable pageable) {

        Page<Qna> page = qnaService.findPublicPage(boardType, pageable);
        return ResponseEntity.ok(toSummaryPage(page));
    }

    /**
     * 문의 상세.
     *
     * <p>{@code principal} 이 {@code null} 이어도 된다 — 비로그인도 공개 문의는 읽는다.
     * 이 엔드포인트는 인증 필터를 통과하되 인증을 <b>요구하지</b> 않는다.
     */
    @Operation(summary = "문의 상세",
               description = "비밀글은 작성자와 관리자만 볼 수 있다. 남이 열면 404 다.")
    @GetMapping("/qnas/{id}")
    public ResponseEntity<QnaDetailResponse> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        Long viewerId = principal == null ? null : principal.id();
        qnaService.increaseViewCount(id);

        Qna qna = qnaService.findPublic(id, viewerId, false);
        return ResponseEntity.ok(toDetail(qna, viewerId));
    }

    @Operation(summary = "문의 등록", description = "로그인이 필요하다.")
    @PostMapping("/qnas")
    public ResponseEntity<QnaDetailResponse> write(
            @Valid @RequestBody QnaRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        requireLogin(principal);

        Qna qna = qnaService.write(principal.id(), request.boardType(), request.title(),
                request.question(), request.secretOrDefault(),
                request.productIdFor(request.boardType()));

        return ResponseEntity.status(HttpStatus.CREATED).body(toDetail(qna, principal.id()));
    }

    @Operation(summary = "문의 수정",
               description = "작성자 본인만. 답변이 달린 뒤에는 수정할 수 없다.")
    @PutMapping("/qnas/{id}")
    public ResponseEntity<QnaDetailResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody QnaRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        requireLogin(principal);

        Qna qna = qnaService.update(id, principal.id(), request.title(), request.question(),
                request.secretOrDefault(), request.productId());

        return ResponseEntity.ok(toDetail(qna, principal.id()));
    }

    @Operation(summary = "문의 삭제",
               description = "작성자 본인만. 답변이 달린 뒤에는 삭제할 수 없다.")
    @DeleteMapping("/qnas/{id}")
    public ResponseEntity<Void> withdraw(@PathVariable Long id,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        requireLogin(principal);
        qnaService.withdraw(id, principal.id());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내가 쓴 문의", description = "게시판 구분 없이 최신순으로 돌려준다.")
    @GetMapping("/me/qnas")
    public ResponseEntity<PageResponse<QnaSummaryResponse>> mine(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 10) Pageable pageable) {

        requireLogin(principal);
        return ResponseEntity.ok(toSummaryPage(qnaService.findMinePage(principal.id(), pageable)));
    }

    // ---------- 조립 ----------

    /**
     * 목록 조립.
     *
     * <p>회원명·상품명을 <b>페이지 단위로 한 번에</b> 모아 온다. 줄마다 조회하면 15줄짜리
     * 목록 하나가 31번의 질의가 된다.
     */
    private PageResponse<QnaSummaryResponse> toSummaryPage(Page<Qna> page) {
        List<Qna> rows = page.getContent();
        Map<Long, String> names = qnaService.maskedAuthorNames(rows);
        Map<Long, String> products = qnaService.productNames(rows);

        return PageResponse.of(page, qna -> QnaSummaryResponse.of(
                qna,
                names.getOrDefault(qna.getUserId(), NAME_FALLBACK),
                qna.getProductId() == null ? null : products.get(qna.getProductId())));
    }

    private QnaDetailResponse toDetail(Qna qna, Long viewerId) {
        List<Qna> one = List.of(qna);
        Map<Long, String> names = qnaService.maskedAuthorNames(one);
        Map<Long, String> products = qnaService.productNames(one);

        return QnaDetailResponse.of(
                qna,
                names.getOrDefault(qna.getUserId(), NAME_FALLBACK),
                qna.getProductId() == null ? null : products.get(qna.getProductId()),
                qna.isOwnedBy(viewerId));
    }

    /** 회원 행을 찾지 못한 경우(탈퇴 등). 빈 칸을 두면 표가 무너진다. */
    private static final String NAME_FALLBACK = "회원";

    private void requireLogin(UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "비로그인 문의 쓰기 시도");
        }
    }
}
