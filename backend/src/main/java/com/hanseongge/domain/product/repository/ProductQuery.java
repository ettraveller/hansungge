package com.hanseongge.domain.product.repository;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.product.entity.PriceType;
import com.hanseongge.domain.product.entity.ProductStatus;

import java.util.List;

/**
 * 해석이 끝난 상품 검색 조건.
 *
 * <p>{@link com.hanseongge.domain.product.dto.ProductSearchCondition} 은 <b>클라이언트가 보낸
 * 요청</b>이고, 이 레코드는 <b>서비스가 해석한 결과</b>다. 둘을 나눈 이유:
 * <ul>
 *   <li>{@code categoryId} 하나가 <b>카테고리 여러 개</b>로 펼쳐진다 (상위 선택 시 하위 포함)</li>
 *   <li>{@code visibleOnly} 는 요청 값이 아니라 <b>서버가 정하는 값</b>이다. 요청 DTO 에 두면
 *       공개 API 가 클라이언트 파라미터를 그대로 넘기다 숨김 상품을 노출한다</li>
 * </ul>
 *
 * @param categoryIds 비어 있으면 카테고리 조건을 걸지 않는다
 * @param visibleOnly true 면 고객 노출 가능한 상품만 (active + ON_SALE/SOLD_OUT)
 */
public record ProductQuery(
        List<Long> categoryIds,
        String keyword,
        MetalType metalType,
        PriceType priceType,
        ProductStatus status,
        Boolean active,
        boolean visibleOnly
) {

    public boolean hasCategories() {
        return categoryIds != null && !categoryIds.isEmpty();
    }

    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }
}
