package com.hanseongge.domain.product.controller;

import com.hanseongge.domain.product.dto.CategoryResponse;
import com.hanseongge.domain.product.dto.ProductDetailResponse;
import com.hanseongge.domain.product.dto.ProductSearchCondition;
import com.hanseongge.domain.product.dto.ProductSummaryResponse;
import com.hanseongge.domain.product.service.CategoryService;
import com.hanseongge.domain.product.service.ProductQueryService;
import com.hanseongge.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 공개 상품 API (설계서 §5 공개, §9 사용자 메뉴 {@code /shop}).
 *
 * <h2>노출 조건은 서버가 강제한다</h2>
 * 검색 조건에 {@code status} · {@code active} 를 담아 보내도 무시된다. 클라이언트가 끌 수
 * 있게 두면 <b>숨김 상품을 파라미터 하나로 열람</b>할 수 있다. 숨김 상품의 상세를 직접
 * 요청하면 403 이 아니라 <b>404</b> 다 — 403 이면 "여기 뭔가 있다"는 사실이 새어 나간다.
 *
 * <p>가격은 이 응답에 이미 계산되어 담긴다. 프론트엔드가 가격을 계산할 일이 없어야 한다
 * (코딩규칙 P1).
 */
@Tag(name = "상품", description = "공개 상품 목록/상세")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PublicProductController {

    private final ProductQueryService productQueryService;
    private final CategoryService categoryService;

    @Operation(summary = "카테고리 목록", description = "활성 카테고리만.")
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> categories() {
        return ResponseEntity.ok(categoryService.findActive().stream()
                .map(CategoryResponse::from)
                .toList());
    }

    @Operation(summary = "상품 목록",
            description = "판매중·품절 상품만 나간다. 가격은 대표가(최저 옵션가)이며, "
                    + "시세가 없거나 노후면 '시세문의'가 표시된다.")
    @GetMapping("/products")
    public ResponseEntity<PageResponse<ProductSummaryResponse>> products(
            @ModelAttribute ProductSearchCondition condition,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(productQueryService.list(condition, pageable));
    }

    @Operation(summary = "상품 상세",
            description = "옵션 목록과 옵션별 가격을 함께 돌려준다. 재고 수량은 노출하지 않고 "
                    + "구매 가능 여부만 알려준다.")
    @GetMapping("/products/{id}")
    public ResponseEntity<ProductDetailResponse> product(@PathVariable Long id) {
        return ResponseEntity.ok(productQueryService.detail(id));
    }
}
