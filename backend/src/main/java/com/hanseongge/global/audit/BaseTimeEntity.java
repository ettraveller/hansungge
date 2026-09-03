package com.hanseongge.global.audit;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * 생성/수정 시각 공통 컬럼 (JPA Auditing).
 *
 * <p>주의: append-only 테이블({@code market_prices_effective}, {@code order_items})은
 * 이 클래스를 상속하지 않는다. {@code updated_at} 컬럼의 존재 자체가 "이 행은 수정된다"는
 * 잘못된 신호이기 때문이다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
