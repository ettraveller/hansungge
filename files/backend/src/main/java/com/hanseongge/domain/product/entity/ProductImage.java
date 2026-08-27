package com.hanseongge.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 이미지 ({@code product_images}).
 *
 * <p><b>URL 이 아니라 Object Storage 키를 저장한다.</b> URL 을 넣으면 스토리지 엔드포인트나
 * CDN 도메인이 바뀌는 날 전 행이 무효가 되고, 그때 일괄 UPDATE 를 돌려야 한다.
 * 키만 두면 URL 조립은 조회 시점의 설정이 결정한다.
 *
 * <p>업로드 자체(NCP Object Storage 연동)는 STEP 10 이다. 여기서는 <b>이미 업로드된 키를
 * 상품에 연결·정렬·해제</b>하는 것만 다룬다.
 */
@Entity
@Getter
@Table(name = "product_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "object_key", length = 300, nullable = false)
    private String objectKey;

    @Column(name = "sort", nullable = false)
    private int sort;

    private ProductImage(Long productId, String objectKey, int sort) {
        this.productId = productId;
        this.objectKey = objectKey;
        this.sort = sort;
    }

    public static ProductImage create(Long productId, String objectKey, int sort) {
        return new ProductImage(productId, objectKey, sort);
    }

    public void changeSort(int sort) {
        this.sort = sort;
    }
}
