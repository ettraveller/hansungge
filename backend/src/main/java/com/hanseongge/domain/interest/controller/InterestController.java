package com.hanseongge.domain.interest.controller;

import com.hanseongge.domain.interest.dto.MyPageSummaryResponse;
import com.hanseongge.domain.interest.service.InterestService;
import com.hanseongge.domain.interest.service.MyPageSummaryService;
import com.hanseongge.domain.product.dto.ProductSummaryResponse;
import com.hanseongge.global.auth.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 찜한 상품 · 최근 본 상품. 전 구간 로그인 필요.
 *
 * <p>비회원 찜하기는 만들지 않는다 — 브라우저에만 있는 목록은 기기를 바꾸면 사라지고,
 * 서버에 두면 익명 데이터를 무한정 보관하게 된다 (장바구니와 같은 판단).
 */
@Slf4j
@Tag(name = "관심상품", description = "찜한 상품 / 최근 본 상품")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class InterestController {

    private final InterestService interestService;
    private final MyPageSummaryService summaryService;

    @Operation(summary = "마이페이지 요약", description = "총 주문 건수와 상태별 개수, 찜한 상품 수.")
    @GetMapping("/summary")
    public ResponseEntity<MyPageSummaryResponse> summary(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(summaryService.of(principal.id()));
    }

    // ---------- 찜한 상품 ----------

    @Operation(summary = "찜한 상품 목록",
            description = "금액은 지금 시세로 다시 계산한 값이다. 찜한 시점 금액을 보관하지 않는다.")
    @GetMapping("/wishlist")
    public ResponseEntity<List<ProductSummaryResponse>> wishlist(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(interestService.wishlist(principal.id()));
    }

    @Operation(summary = "찜하기", description = "이미 찜했으면 아무 일도 하지 않는다. 두 번 눌러도 오류가 아니다.")
    @PostMapping("/wishlist/{productId}")
    public ResponseEntity<Void> add(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable Long productId) {
        interestService.addToWishlist(principal.id(), productId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "찜 해제")
    @DeleteMapping("/wishlist/{productId}")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long productId) {
        interestService.removeFromWishlist(principal.id(), productId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "찜 여부", description = "상품 상세의 하트 초기 상태용.")
    @GetMapping("/wishlist/{productId}")
    public ResponseEntity<Map<String, Boolean>> check(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long productId) {
        return ResponseEntity.ok(Map.of("wishlisted", interestService.isWishlisted(principal.id(), productId)));
    }

    // ---------- 최근 본 상품 ----------

    @Operation(summary = "최근 본 상품")
    @GetMapping("/recently-viewed")
    public ResponseEntity<List<ProductSummaryResponse>> recentlyViewed(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(interestService.recentlyViewed(principal.id(), Math.max(size, 1)));
    }

    /**
     * 본 것으로 기록한다.
     *
     * <p><b>실패해도 204 로 답한다.</b> 이 기록이 안 됐다고 상품 상세 화면에 오류를 띄울
     * 이유가 없다 — 고객이 보러 온 것은 상품이지 기록이 아니다.
     */
    @Operation(summary = "본 상품으로 기록", description = "상품 상세를 열 때 부른다. 실패해도 화면에 영향을 주지 않는다.")
    @PostMapping("/recently-viewed/{productId}")
    public ResponseEntity<Void> markViewed(@AuthenticationPrincipal UserPrincipal principal,
                                           @PathVariable Long productId) {
        try {
            interestService.markViewed(principal.id(), productId);
        } catch (RuntimeException e) {
            log.warn("[INTEREST] 최근 본 상품 기록 실패 userId={} productId={}", principal.id(), productId, e);
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "최근 본 상품 비우기")
    @DeleteMapping("/recently-viewed")
    public ResponseEntity<Void> clearRecentlyViewed(
            @AuthenticationPrincipal UserPrincipal principal) {
        interestService.clearRecentlyViewed(principal.id());
        return ResponseEntity.noContent().build();
    }
}
