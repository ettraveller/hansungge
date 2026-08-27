package com.hanseongge.domain.pricing.controller;

import com.hanseongge.domain.pricing.dto.PublicPriceQuoteResponse;
import com.hanseongge.domain.pricing.service.PriceCalculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 공개 상품 가격 API.
 *
 * <p>상품 조회 API 자체(목록·상세)는 STEP 8~9 다. 여기서는 <b>가격만</b> 다룬다 —
 * 가격은 상품 정보와 달리 시세에 따라 계속 변하므로, 프론트엔드가 상품 정보와
 * 별개 주기로 갱신할 수 있어야 한다.
 *
 * <p>가격을 표시할 수 없는 경우에도 <b>200 + "시세문의"</b> 다. 시세가 없는 것은 장애가
 * 아니라 정상적으로 발생하는 상태이기 때문이다 (개발계획 §12.4, 시나리오 S6).
 */
@Tag(name = "상품 가격", description = "공개 상품 가격 조회")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class PublicProductPriceController {

    private final PriceCalculationService priceCalculationService;

    @Operation(summary = "상품 가격",
            description = "현재 시세 기준으로 계산한다. 시세가 없거나 노후면 금액 대신 '시세문의'를 돌려준다. "
                    + "이 응답에 0원이 담기는 경로는 존재하지 않는다.")
    @GetMapping("/{productId}/price")
    public ResponseEntity<PublicPriceQuoteResponse> price(@PathVariable Long productId,
                                                          @RequestParam(required = false) Long optionId) {
        return ResponseEntity.ok(
                PublicPriceQuoteResponse.from(priceCalculationService.quote(productId, optionId)));
    }

    @Operation(summary = "옵션별 가격",
            description = "활성 옵션 전체의 가격. 상세페이지에서 옵션 선택 UI 를 그릴 때 쓴다.")
    @GetMapping("/{productId}/prices")
    public ResponseEntity<List<PublicPriceQuoteResponse>> prices(@PathVariable Long productId) {
        return ResponseEntity.ok(priceCalculationService.quoteAllOptions(productId).stream()
                .map(PublicPriceQuoteResponse::from)
                .toList());
    }
}
