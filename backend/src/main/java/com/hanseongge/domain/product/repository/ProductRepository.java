package com.hanseongge.domain.product.repository;

import com.hanseongge.domain.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 상품 저장소.
 *
 * <p>삭제 메서드를 애플리케이션에서 쓰지 않는다. 상품을 지우면 그 상품을 담은 과거 주문의
 * 상품 정보가 사라진다. 판매 종료는 {@code status=DISCONTINUED} 로 표현한다.
 */
public interface ProductRepository extends JpaRepository<Product, Long>, ProductRepositoryCustom {

    boolean existsByCategoryId(Long categoryId);
}
