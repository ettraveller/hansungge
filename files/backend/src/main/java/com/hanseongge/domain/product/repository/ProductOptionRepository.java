package com.hanseongge.domain.product.repository;

import com.hanseongge.domain.product.entity.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductOptionRepository extends JpaRepository<ProductOption, Long> {

    List<ProductOption> findByProductIdAndActiveTrueOrderBySortAscIdAsc(Long productId);

    List<ProductOption> findByProductIdOrderBySortAscIdAsc(Long productId);

    long countByProductIdAndActiveTrue(Long productId);
}
