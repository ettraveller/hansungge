package com.hanseongge.domain.pricing.controller;

import com.hanseongge.domain.pricing.dto.PriceQuoteResponse;
import com.hanseongge.domain.product.dto.ProductDetailResponse;
import com.hanseongge.domain.product.dto.ProductDraftDetailRequest;
import com.hanseongge.domain.product.dto.ProductDraftPriceRequest;
import com.hanseongge.domain.product.service.ProductService;
import com.hanseongge.domain.pricing.service.PriceCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 가격 미리보기 (개발계획 §13.7 {@code context='ADMIN_PREVIEW'}).
 *
 * <p>공개 전 상품(HIDDEN)의 가격도 계산한다. 그 기능이 없으면 운영자가 가격을 확인하려고
 * <b>일단 공개했다가 되돌리는</b> 위험한 절차를 쓰게 된다.
 *
 * <p>캐시를 타지 않는다 — 미리보기는 항상 지금 값이어야 한다.
 */
@Tag(name = "관리자 가격", description = "상품 가격 미리보기")
@RestController
@RequestMapping("/api/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATOR')")
public class AdminProductPriceController {

    private final PriceCalculationService priceCalculationService;
    private final ProductService productService;

    @Operation(summary = "가격 미리보기",
            description = "현재 시세·보정·부가세·반올림을 적용한 산출 결과와 구성요소 내역을 돌려준다. "
                    + "원본 시세와 보정액이 포함되므로 관리자 전용이다. "
                    + "시세가 없거나 노후면 status=INQUIRY_ONLY 이고 금액은 null 이다.")
    @GetMapping("/{productId}/price-preview")
    public ResponseEntity<PriceQuoteResponse> preview(@PathVariable Long productId,
                                                      @RequestParam(required = false) Long optionId) {
        return ResponseEntity.ok(
                PriceQuoteResponse.from(priceCalculationService.preview(productId, optionId)));
    }

    @Operation(summary = "옵션별 가격 일괄 미리보기",
            description = "활성 옵션 전체의 가격을 한 번에 돌려준다. 옵션이 없으면 1건이다.")
    @GetMapping("/{productId}/price-preview/options")
    public ResponseEntity<List<PriceQuoteResponse>> previewOptions(@PathVariable Long productId) {
        return ResponseEntity.ok(priceCalculationService.quoteAllOptions(productId).stream()
                .map(PriceQuoteResponse::from)
                .toList());
    }

    /**
     * 등록 전 상세 미리보기.
     *
     * <p>상품 id 가 없다. 등록 폼을 채우는 중이라 아직 상품이 없고, 그래서 위의
     * {@code /{productId}/price-preview} 를 쓸 수 없다.
     *
     * <p>응답이 <b>공개 상세와 같은 모양</b>이다({@code GET /api/v1/products/{id}} 와
     * 같은 {@link ProductDetailResponse}). 관리자 화면이 그 응답으로 고객용 상세
     * 컴포넌트를 그대로 그린다 — 비슷하게 다시 그리면 쇼핑몰이 바뀔 때 미리보기만
     * 낡아서 "미리보기와 실제가 다르다"가 된다.
     *
     * <p><b>POST 지만 아무것도 저장하지 않는다.</b> 입력값을 본문으로 받아야 해서
     * POST 일 뿐이다 — 가격 재료만 일곱 개에 이미지 키 목록까지 붙으므로 쿼리스트링에
     * 담을 수 없다.
     */
    @Operation(summary = "등록 전 상세 미리보기",
            description = "공개 상세와 같은 응답 모양이다. 상품을 만들지 않고, 아무것도 저장하지 않는다. "
                    + "옵션은 등록 시 자동 생성될 기본 옵션 하나를 가정한다. "
                    + "등록 직후 상품은 HIDDEN 이므로 status 는 HIDDEN 으로 오고, 금액은 그대로 계산된다.")
    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/detail-preview")
    public ResponseEntity<ProductDetailResponse> previewDraftDetail(
            @Valid @RequestBody ProductDraftDetailRequest request) {
        return ResponseEntity.ok(productService.previewDraftDetail(request));
    }
}
