package com.hanseongge.domain.product.entity;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;

import java.math.BigDecimal;

/**
 * 상품의 <b>가격을 만드는 재료</b> (개발계획 §9.1).
 *
 * <p>판매가가 아니다. 판매가는 저장하지 않고 조회 시점에 계산된다 — 시세가 바뀌면
 * 상품 가격이 자동으로 바뀌는 것은 이 구조의 부작용이 아니라 정의다.
 *
 * <p>이 값들을 한 덩어리로 묶은 이유: {@code price_type} 에 따라 <b>필수 필드가 달라진다.</b>
 * 흩어 두면 그 정합성 검사가 서비스·컨트롤러·엔티티에 나뉘어 한 곳만 빠뜨려도 통과한다.
 * V3 의 {@code chk_price_type_fields} 와 같은 규칙을 여기서 한 번에 확인한다.
 *
 * <p>⚠️ {@code purity} 는 <b>상세페이지 표기 전용</b>이며 어떤 계산식에도 들어가지 않는다
 * (개발계획 §9.2). {@code metal_type=GOLD_18K} 자체가 순도를 내포하고 18K 시세가 별도로
 * 고시되므로, 여기에 0.75 를 곱하면 <b>순도가 이중 적용</b>된다.
 */
public record ProductPricing(
        MetalType metalType,
        PriceType priceType,
        BigDecimal weightG,
        BigDecimal purity,
        BigDecimal laborCost,
        BigDecimal multiplier,
        BigDecimal fixedPrice
) {

    public ProductPricing {
        if (priceType == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "가격 방식을 선택해 주세요.");
        }
        laborCost = laborCost == null ? BigDecimal.ZERO : laborCost;
        multiplier = multiplier == null ? BigDecimal.ONE : multiplier;

        validate(priceType, metalType, weightG, laborCost, multiplier, fixedPrice);
    }

    /**
     * V3 {@code chk_price_type_fields} 와 동일한 규칙 + 값 범위.
     *
     * <p>DB 제약이 있는데 여기서 또 보는 이유: 제약 위반은 <b>500 으로 나간다.</b>
     * 관리자 입력 오류는 400 이어야 하고, "무엇이 잘못됐는지"가 응답에 담겨야 한다.
     */
    private static void validate(PriceType priceType, MetalType metalType, BigDecimal weightG,
                                 BigDecimal laborCost, BigDecimal multiplier, BigDecimal fixedPrice) {

        if (priceType.isMarketLinked()) {
            if (metalType == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "시세 연동 상품에는 금속 종류가 필요합니다.");
            }
            if (weightG == null || weightG.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "시세 연동 상품에는 0보다 큰 중량이 필요합니다.");
            }
        } else {
            if (fixedPrice == null || fixedPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "고정가 상품에는 0보다 큰 판매가가 필요합니다.");
            }
        }

        if (laborCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "공임은 0 이상이어야 합니다.");
        }
        if (multiplier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "배수는 0보다 커야 합니다.");
        }
        // MARKET_MULTIPLIER 인데 배수가 1인 상태는 막지 않는다. MARKET_LINKED 와 결과가
        // 같아 의도가 아닐 가능성이 높지만, 배수를 잠시 1로 되돌리는 운영이 실제로 있다.
        // 막으면 그 운영이 "배수를 0.9999 로 넣는" 편법으로 우회된다.
    }
}
