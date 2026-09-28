package com.hanseongge.domain.interest.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 찜한 상품 ({@code wishlist_items}).
 *
 * <p>금액을 담지 않는다. 시세 상품이라 찜한 시점의 값은 다음 날이면 틀린 값이고,
 * 고객이 보고 싶은 것은 <b>지금 얼마인가</b>이다. 목록을 열 때마다 다시 계산한다.
 */
@Entity
@Getter
@Table(name = "wishlist_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static WishlistItem of(Long userId, Long productId) {
        WishlistItem item = new WishlistItem();
        item.userId = userId;
        item.productId = productId;
        item.createdAt = OffsetDateTime.now();
        return item;
    }
}
