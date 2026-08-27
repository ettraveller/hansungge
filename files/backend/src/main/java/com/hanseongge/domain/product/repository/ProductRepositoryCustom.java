package com.hanseongge.domain.product.repository;

import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** 조건이 동적으로 붙는 상품 검색. 구현은 QueryDSL. */
public interface ProductRepositoryCustom {

    /**
     * @param query 서비스가 해석을 마친 조건. 카테고리는 이미 하위까지 펼쳐져 있고,
     *              노출 범위도 서버가 정한 값이 들어 있다
     */
    Page<Product> search(ProductQuery query, Pageable pageable);

    /** 카테고리에 속한 상품 수. 카테고리 삭제 가능 여부 판단에 쓴다. */
    long countByCategory(Long categoryId, List<ProductStatus> excludedStatuses);
}
