package com.hanseongge.domain.product.repository;

import com.hanseongge.domain.product.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderBySortAscIdAsc(Long productId);

    long deleteByProductIdAndObjectKey(Long productId, String objectKey);
}
