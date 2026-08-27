package com.hanseongge.domain.product.service;

import com.hanseongge.domain.pricing.dto.PublicPriceQuoteResponse;
import com.hanseongge.domain.pricing.service.PriceCalculationService;
import com.hanseongge.domain.pricing.service.PriceQuote;
import com.hanseongge.domain.product.dto.ProductDetailResponse;
import com.hanseongge.domain.product.dto.ProductOptionResponse;
import com.hanseongge.domain.product.dto.ProductSearchCondition;
import com.hanseongge.domain.product.dto.ProductSummaryResponse;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductImage;
import com.hanseongge.domain.product.entity.ProductOption;
import com.hanseongge.global.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 공개 상품 조회 — 상품 정보 + 계산된 가격을 합쳐 내보낸다.
 *
 * <h2>왜 별도 서비스인가</h2>
 * {@link ProductService} 는 상품을 <b>관리</b>하고, {@link PriceCalculationService} 는 가격을
 * <b>계산</b>한다. 둘을 합쳐 화면 모양으로 만드는 일은 어느 쪽 책임도 아니다. 관리 서비스가
 * 가격 엔진을 알게 되면 상품 등록 코드가 시세 도메인에 묶인다.
 *
 * <h2>가격 산출 실패가 목록 전체를 죽이지 않는다</h2>
 * 상품 하나의 설정이 잘못돼 있어도(중량 누락 등) 그 줄만 "시세문의"로 그리고 나머지는
 * 정상 표시한다. 목록은 수십 개 상품 중 하나 때문에 통째로 500 이 되면 안 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductQueryService {

    private final ProductService productService;
    private final PriceCalculationService priceCalculationService;

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> list(ProductSearchCondition condition, Pageable pageable) {
        // 공개 조회는 노출 조건을 서버가 강제한다. 클라이언트 파라미터로 뚫을 수 없다.
        Page<Product> page = productService.search(condition.forPublic(), true, pageable);
        return PageResponse.of(page, this::toSummary);
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse detail(Long productId) {
        Product product = productService.findVisible(productId);

        List<String> imageKeys = productService.findImages(productId).stream()
                .map(ProductImage::getObjectKey)
                .toList();

        List<ProductOption> options = productService.findActiveOptions(productId);

        List<ProductOptionResponse> optionResponses = new ArrayList<>();
        List<PublicPriceQuoteResponse> prices = new ArrayList<>();
        for (ProductOption option : options) {
            optionResponses.add(ProductOptionResponse.forPublic(option));
            prices.add(PublicPriceQuoteResponse.from(quoteSafely(productId, option.getId())));
        }

        return ProductDetailResponse.of(product, imageKeys, optionResponses, prices);
    }

    // =================================================================
    // 내부
    // =================================================================

    private ProductSummaryResponse toSummary(Product product) {
        List<ProductOption> options = productService.findActiveOptions(product.getId());
        boolean multipleOptions = options.size() > 1;

        PriceQuote representative = priceCalculationService.representativeQuote(product.getId())
                .orElseGet(() -> quoteSafely(product.getId(),
                        options.isEmpty() ? null : options.get(0).getId()));

        return ProductSummaryResponse.of(product, representative, multipleOptions);
    }

    /**
     * 가격 산출. 실패해도 예외를 밖으로 내보내지 않는다.
     *
     * <p>{@link PriceCalculationService} 는 "시세 없음"을 이미 상태로 돌려주므로 여기서
     * 잡히는 것은 <b>상품 설정 오류</b>(중량 누락, 배수 0 등)뿐이다. 그런 상품은 화면에서
     * "시세문의"로 보이고, 사실은 WARN 로그로 운영자에게 알린다.
     */
    private PriceQuote quoteSafely(Long productId, Long optionId) {
        try {
            return priceCalculationService.quote(productId, optionId);
        } catch (Exception e) {
            log.warn("[PRODUCT] 가격 산출 실패 — productId={} optionId={} 사유={}. "
                    + "해당 상품만 '시세문의'로 표시합니다.", productId, optionId, e.getMessage());
            return PriceQuote.unavailable(
                    com.hanseongge.domain.pricing.service.PriceStatus.INQUIRY_ONLY,
                    productId, optionId, "가격을 확인할 수 없습니다. 문의해 주세요.");
        }
    }
}
