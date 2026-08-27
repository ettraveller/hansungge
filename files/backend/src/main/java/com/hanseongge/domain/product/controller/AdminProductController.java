package com.hanseongge.domain.product.controller;

import com.hanseongge.domain.product.dto.ProductOptionRequest;
import com.hanseongge.domain.product.dto.ProductOptionResponse;
import com.hanseongge.domain.product.dto.ProductRequest;
import com.hanseongge.domain.product.dto.ProductResponse;
import com.hanseongge.domain.product.dto.ProductSearchCondition;
import com.hanseongge.domain.product.dto.ProductStatusChangeRequest;
import com.hanseongge.domain.product.dto.StockAdjustRequest;
import com.hanseongge.domain.product.entity.ProductImage;
import com.hanseongge.domain.product.service.ProductService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 상품 관리 API (설계서 §5 — {@code CRUD /api/admin/products}).
 *
 * <h2>DELETE 가 없다</h2>
 * 상품을 지우면 그 상품을 담은 <b>과거 주문의 상품 정보가 사라진다.</b> 판매 종료는
 * {@code PATCH /{id}/status} 로 {@code DISCONTINUED} 처리한다.
 *
 * <h2>등록 직후에는 노출되지 않는다</h2>
 * 새 상품은 {@code HIDDEN} 으로 만들어진다. 재고·이미지·가격을 확인한 뒤
 * {@code PATCH /{id}/status} 로 판매를 시작한다. 그 전에는
 * {@code GET /{id}/price-preview}(가격 미리보기)로 금액을 확인할 수 있다.
 */
@Tag(name = "관리자 상품", description = "상품 · 옵션 · 재고 · 이미지 관리")
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATOR')")
public class AdminProductController {

    private final ProductService productService;

    // ---------- 조회 ----------

    @Operation(summary = "상품 목록",
            description = "숨김·판매종료·비활성 상품도 조회된다. 관리자 화면 전용이다.")
    @GetMapping
    public ResponseEntity<PageResponse<ProductResponse>> list(
            @ModelAttribute ProductSearchCondition condition,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(PageResponse.of(
                productService.search(condition, false, pageable), ProductResponse::from));
    }

    @Operation(summary = "상품 단건 조회")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(ProductResponse.from(productService.find(id)));
    }

    @Operation(summary = "옵션 목록", description = "비활성 옵션도 포함한다. 재고 수량이 노출된다.")
    @GetMapping("/{id}/options")
    public ResponseEntity<List<ProductOptionResponse>> options(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findAllOptions(id).stream()
                .map(ProductOptionResponse::forAdmin)
                .toList());
    }

    @Operation(summary = "이미지 목록")
    @GetMapping("/{id}/images")
    public ResponseEntity<List<String>> images(@PathVariable Long id) {
        return ResponseEntity.ok(productService.findImages(id).stream()
                .map(ProductImage::getObjectKey)
                .toList());
    }

    // ---------- 등록 / 수정 (MANAGER 이상) ----------

    @Operation(summary = "상품 등록",
            description = "HIDDEN 상태로 생성되고, 재고를 담을 기본 옵션 1개가 자동 생성된다. "
                    + "판매가는 입력하지 않는다 — 가격은 시세·중량·공임으로 계산된다.")
    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request,
                                                  @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(
                productService.create(request.categoryId(), request.name(), request.toPricing(),
                        request.description(), request.thumbnailKey(),
                        request.defaultOptionName(), request.initialStock(), actor.loginId())));
    }

    @Operation(summary = "상품 수정",
            description = "가격 재료가 바뀌면 가격 캐시가 무효화되어 다음 조회부터 반영된다.")
    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody ProductRequest request,
                                                  @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(ProductResponse.from(
                productService.update(id, request.categoryId(), request.name(), request.toPricing(),
                        request.description(), request.thumbnailKey(), actor.loginId())));
    }

    @Operation(summary = "판매 상태 변경",
            description = "ON_SALE 로 바꾸려면 활성 옵션이 최소 1개 있어야 한다. 사유는 필수다.")
    @PreAuthorize("hasRole('MANAGER')")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ProductResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody ProductStatusChangeRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {

        return ResponseEntity.ok(ProductResponse.from(productService.changeStatus(
                id, request.status(), request.activeOrDefault(), request.reason(), actor.loginId())));
    }

    // ---------- 옵션 ----------

    @Operation(summary = "옵션 추가")
    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/options")
    public ResponseEntity<ProductOptionResponse> addOption(@PathVariable Long id,
                                                           @Valid @RequestBody ProductOptionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductOptionResponse.forAdmin(
                productService.addOption(id, request.name(), request.weightG(),
                        request.additionalLaborCost(), request.stockOrZero(), request.sort())));
    }

    @Operation(summary = "옵션 수정",
            description = "마지막 활성 옵션은 비활성화할 수 없다 — 재고가 존재할 곳이 사라진다. "
                    + "재고 수량은 이 API 가 아니라 재고 조정 API 로 바꾼다.")
    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/{id}/options/{optionId}")
    public ResponseEntity<ProductOptionResponse> updateOption(
            @PathVariable Long id,
            @PathVariable Long optionId,
            @Valid @RequestBody ProductOptionRequest request) {

        return ResponseEntity.ok(ProductOptionResponse.forAdmin(productService.updateOption(
                id, optionId, request.name(), request.weightG(), request.additionalLaborCost(),
                request.sort(), request.activeOrDefault())));
    }

    @Operation(summary = "재고 조정",
            description = "실사 결과를 절대 수량으로 반영한다. 증감이 아니라 절대값이라 "
                    + "요청이 중복 도착해도 재고가 두 번 깎이지 않는다. 사유는 필수다.")
    @PreAuthorize("hasRole('MANAGER')")
    @PatchMapping("/{id}/options/{optionId}/stock")
    public ResponseEntity<ProductOptionResponse> adjustStock(
            @PathVariable Long id,
            @PathVariable Long optionId,
            @Valid @RequestBody StockAdjustRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {

        return ResponseEntity.ok(ProductOptionResponse.forAdmin(productService.adjustStock(
                id, optionId, request.stock(), request.reason(), actor.loginId())));
    }

    // ---------- 이미지 ----------

    @Operation(summary = "이미지 목록 교체",
            description = "보낸 순서대로 정렬된다. Object Storage 업로드는 STEP 10 이며 "
                    + "여기서는 이미 업로드된 키만 연결한다.")
    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/{id}/images")
    public ResponseEntity<List<String>> replaceImages(@PathVariable Long id,
                                                      @RequestBody List<String> objectKeys) {
        return ResponseEntity.ok(productService.replaceImages(id, objectKeys).stream()
                .map(ProductImage::getObjectKey)
                .toList());
    }
}
