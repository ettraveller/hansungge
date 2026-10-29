package com.hanseongge.domain.product.repository;

import com.hanseongge.domain.product.entity.ProductImage;
import com.hanseongge.domain.product.entity.ProductImageKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    /**
     * 한 갈래를 순서대로.
     *
     * <p>{@code sort} 만으로 정렬하지 않는다. 같은 값이 섞일 수 있고(손으로 넣은 행,
     * 예전 데이터) 그러면 조회할 때마다 순서가 달라져 화면의 사진이 이유 없이 바뀐다.
     */
    List<ProductImage> findByProductIdAndKindOrderBySortAscIdAsc(Long productId,
                                                                 ProductImageKind kind);

    /** 갈래를 가리지 않는 전체. 상품을 지울 때 쓴다. */
    List<ProductImage> findByProductIdOrderBySortAscIdAsc(Long productId);

    long deleteByProductIdAndObjectKey(Long productId, String objectKey);
}
