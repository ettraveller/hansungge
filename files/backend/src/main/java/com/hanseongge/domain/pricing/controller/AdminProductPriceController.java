package com.hanseongge.domain.pricing.controller;

import com.hanseongge.domain.pricing.dto.PriceQuoteResponse;
import com.hanseongge.domain.pricing.service.PriceCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
