package com.hanseongge.support;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.product.entity.PriceType;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductOption;
import com.hanseongge.domain.product.entity.ProductPricing;
import com.hanseongge.domain.product.entity.ProductStatus;

import java.math.BigDecimal;

/**
 * 테스트용 상품·옵션 생성기.
 *
 * <p>STEP 8 부터는 엔티티의 실제 팩토리({@code Product.create} 등)를 쓴다. 리플렉션으로
 * 필드를 밀어 넣으면 <b>애플리케이션이 만들 수 없는 상태</b>의 객체로 테스트하게 되고,
 * 그 테스트는 통과해도 실제 동작을 보장하지 못한다.
 *
 * <p>다만 {@code status} 만은 예외다. 상품은 항상 {@code HIDDEN} 으로 생성되고 관리자
 * API 로만 판매가 시작되는데, 가격 엔진 테스트는 "판매중인 상품"이 전제라 그 상태를
 * 직접 만들어야 한다.
 */
public final class ProductTestFixtures {

    private static final String CREATED_BY = "test_admin";

    private ProductTestFixtures() {
    }

    /** 시세 연동 상품. 바로 쓸 수 있도록 ON_SALE 로 만든다. */
    public static Product marketProduct(Long categoryId, String name, MetalType metal,
                                        PriceType priceType, String weightG,
                                        String laborCost, String multiplier) {
        Product product = Product.create(categoryId, name,
                new ProductPricing(metal, priceType, new BigDecimal(weightG), null,
                        new BigDecimal(laborCost), new BigDecimal(multiplier), null),
                null, null, CREATED_BY);
        product.changeStatus(ProductStatus.ON_SALE, CREATED_BY);
        return product;
    }

    /** 고정가 상품 (시세 미참조). */
    public static Product fixedPriceProduct(Long categoryId, String name, String fixedPrice) {
        Product product = Product.create(categoryId, name,
                new ProductPricing(null, PriceType.FIXED, null, null,
                        BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal(fixedPrice)),
                null, null, CREATED_BY);
        product.changeStatus(ProductStatus.ON_SALE, CREATED_BY);
        return product;
    }

    public static ProductOption option(Long productId, String name, String weightG,
                                       String additionalLaborCost, int stock) {
        return ProductOption.create(productId, name,
                weightG == null ? null : new BigDecimal(weightG),
                new BigDecimal(additionalLaborCost), stock, 0);
    }

    public static void setStatus(Product product, ProductStatus status) {
        product.changeStatus(status, CREATED_BY);
    }

    public static void setActive(Product product, boolean active) {
        product.changeActive(active, CREATED_BY);
    }
}
