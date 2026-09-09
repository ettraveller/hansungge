package com.hanseongge.domain.pricing.service;

import com.hanseongge.domain.product.entity.PriceType;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductOption;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import com.hanseongge.global.util.Money;
import com.hanseongge.global.util.PricePerGram;
import com.hanseongge.global.util.Weight;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code SIMPLE} 가격 모델 → 구성요소 목록 (개발계획 §13.1).
 *
 * <p>V3 의 상품은 {@code price_type} + {@code labor_cost} + {@code multiplier} +
 * {@code fixed_price} 컬럼으로 가격을 표현한다. 이것을 계산 직전에 구성요소로 <b>정규화</b>
 * 하면, 나중에 {@code product_price_components}(주얼리, 결함 D-1)가 들어와도
 * <b>불러오는 곳만 바뀌고 계산·Snapshot·테스트는 그대로</b>다.
 *
 * <p>이 클래스에 DB 접근이 없다. 상품·옵션·시세를 인자로 받아 값만 만든다 —
 * 가격 산식은 틀리면 가장 비싼 코드라 값만으로 전수 검증할 수 있어야 한다.
 *
 * <h2>정규화 규칙 (설계서 §7.1)</h2>
 * <pre>
 * FIXED / MANUAL       fixed_price                                (시세 미참조)
 * MARKET_LINKED        시세×중량 + labor_cost
 * MARKET_PLUS          시세×중량 + labor_cost + 옵션 추가공임
 * MARKET_MULTIPLIER    시세×중량 × multiplier + labor_cost
 * </pre>
 */
@Slf4j
@Component
public class ProductComponentNormalizer {

    private static final int SORT_METAL_BASE = 10;
    private static final int SORT_MULTIPLIER = 20;
    private static final int SORT_LABOR = 30;
    private static final int SORT_OPTION_LABOR = 40;

    /**
     * @param marketPrice 시세 연동 상품일 때의 최종 시세. 미연동이면 null
     * @throws BusinessException 상품 설정이 계산 불가능한 경우(중량 없음, 고정가 없음 등).
     *                           V3 의 {@code chk_price_type_fields} 가 막고 있지만,
     *                           DB 를 직접 고친 데이터가 들어올 수 있어 여기서도 확인한다
     */
    public List<ComponentLine> normalize(Product product, ProductOption option, PricePerGram marketPrice) {
        return switch (product.getPriceType()) {
            case FIXED, MANUAL -> fixedPriceLines(product);
            case MARKET_LINKED, MARKET_PLUS, MARKET_MULTIPLIER -> marketLines(product, option, marketPrice);

            // INQUIRY 는 여기까지 오면 안 된다. 호출자(PriceCalculationService)가
            // 값을 만들기 전에 INQUIRY_ONLY 로 끝냈어야 한다. 도달했다면 그 분기가
            // 빠진 것이므로, 조용히 빈 목록을 돌려주지 않는다 — 빈 목록은 소계 0원이
            // 되어 "0원짜리 상품"이 화면에 걸린다.
            case INQUIRY -> throw new BusinessException(ErrorCode.PRICE_CALCULATION_FAILED,
                    "시세문의 상품은 금액을 산출하지 않습니다: productId=" + product.getId());
        };
    }

    // ---------- 고정가 ----------

    private List<ComponentLine> fixedPriceLines(Product product) {
        Money fixedPrice = product.fixedPriceMoney();
        if (fixedPrice == null || !fixedPrice.isPositive()) {
            throw new BusinessException(ErrorCode.PRICE_CALCULATION_FAILED,
                    "고정가 상품에 fixed_price 가 없습니다: productId=" + product.getId());
        }
        return List.of(ComponentLine.fixedAmount(
                SORT_METAL_BASE, ComponentType.METAL_BASE, "판매가", fixedPrice));
    }

    // ---------- 시세 연동 ----------

    private List<ComponentLine> marketLines(Product product, ProductOption option, PricePerGram marketPrice) {
        if (marketPrice == null) {
            // 호출자가 시세 없음을 INQUIRY_ONLY 로 처리했어야 한다. 여기까지 왔다는 것은 버그다.
            throw new BusinessException(ErrorCode.PRICE_CALCULATION_FAILED,
                    "시세 연동 상품인데 시세가 전달되지 않았습니다: productId=" + product.getId());
        }
        Weight weight = resolveWeight(product, option);
        PriceType priceType = product.getPriceType();

        List<ComponentLine> lines = new ArrayList<>();

        ComponentLine metalBase = ComponentLine.perGramMarket(
                SORT_METAL_BASE, metalLabel(product), marketPrice, weight);
        lines.add(metalBase);

        if (priceType == PriceType.MARKET_MULTIPLIER) {
            addMultiplierLine(lines, product, metalBase.amount());
        }

        Money laborCost = product.laborCostMoney();
        if (laborCost.isPositive()) {
            lines.add(ComponentLine.fixedAmount(SORT_LABOR, ComponentType.LABOR, "공임", laborCost));
        }

        addOptionLaborLine(lines, product, option, priceType);
        return lines;
    }

    /**
     * 배수 가산을 <b>별도 줄</b>로 만든다.
     *
     * <p>{@code 금액 × multiplier} 를 METAL_BASE 에 곱해 넣지 않는 이유: 그러면 상세페이지에
     * "시세 210,000 × 3.75g = 905,625" 처럼 <b>계산이 맞지 않는 줄</b>이 표시된다.
     * 배수분을 따로 두면 각 줄이 자기 산식과 일치하고, 줄 합계도 그대로 소계가 된다.
     */
    private void addMultiplierLine(List<ComponentLine> lines, Product product, Money base) {
        BigDecimal multiplier = product.getMultiplier();
        if (multiplier == null || multiplier.compareTo(BigDecimal.ONE) == 0) {
            return;
        }
        if (multiplier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.PRICE_CALCULATION_FAILED,
                    "multiplier 는 0보다 커야 합니다: productId=%d multiplier=%s"
                            .formatted(product.getId(), multiplier.toPlainString()));
        }
        BigDecimal extraRate = multiplier.subtract(BigDecimal.ONE);
        lines.add(ComponentLine.multiplierOfBase(SORT_MULTIPLIER,
                "배수 가산 (×%s)".formatted(multiplier.stripTrailingZeros().toPlainString()),
                base, extraRate));
    }

    /**
     * 옵션 추가공임은 {@code MARKET_PLUS} 에서만 반영된다 (설계서 §7.1).
     *
     * <p>다른 타입에 값이 설정되어 있으면 <b>조용히 무시하지 않고 WARN</b> 을 남긴다.
     * 운영자가 옵션에 추가공임을 넣어두고 "왜 가격에 반영이 안 되지"로 시간을 쓰는 상황을
     * 로그로라도 잡아내기 위해서다.
     */
    private void addOptionLaborLine(List<ComponentLine> lines, Product product,
                                    ProductOption option, PriceType priceType) {
        if (option == null || !option.hasAdditionalLaborCost()) {
            return;
        }
        if (!priceType.usesOptionAdditionalLabor()) {
            log.warn("""
                    [PRICING] 옵션 추가공임이 설정되어 있으나 price_type={} 은 이를 반영하지 않습니다.
                    productId={} optionId={} additionalLaborCost={}
                    반영하려면 price_type 을 MARKET_PLUS 로 바꾸세요.""",
                    priceType, product.getId(), option.getId(), option.getAdditionalLaborCost());
            return;
        }
        lines.add(ComponentLine.fixedAmount(SORT_OPTION_LABOR, ComponentType.LABOR,
                "옵션 추가공임 (" + option.getName() + ")", option.additionalLaborCostMoney()));
    }

    /** 옵션 중량이 있으면 그쪽이 우선한다 (설계서 §7.1 "option weight 우선"). */
    private Weight resolveWeight(Product product, ProductOption option) {
        Weight weight = option != null && option.weight() != null ? option.weight() : product.weight();
        if (weight == null || !weight.isPositive()) {
            throw new BusinessException(ErrorCode.PRICE_CALCULATION_FAILED,
                    "시세 연동 상품에 중량이 없습니다: productId=" + product.getId());
        }
        return weight;
    }

    private String metalLabel(Product product) {
        return product.getMetalType() == null
                ? "금액"
                : product.getMetalType().displayName() + " 시세";
    }
}
