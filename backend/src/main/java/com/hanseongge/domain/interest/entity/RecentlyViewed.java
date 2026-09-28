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
 * 최근 본 상품 ({@code recently_viewed}).
 *
 * <p>회원당 한 상품에 <b>한 줄</b>이다. 다시 보면 {@link #viewedAt} 만 올린다 —
 * 볼 때마다 줄을 쌓으면 같은 상품이 목록을 도배하고 표가 끝없이 자란다.
 */
@Entity
@Getter
@Table(name = "recently_viewed")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecentlyViewed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "viewed_at", nullable = false)
    private OffsetDateTime viewedAt;

    public static RecentlyViewed of(Long userId, Long productId) {
        RecentlyViewed row = new RecentlyViewed();
        row.userId = userId;
        row.productId = productId;
        row.viewedAt = OffsetDateTime.now();
        return row;
    }

    public void touch() {
        this.viewedAt = OffsetDateTime.now();
    }
}
