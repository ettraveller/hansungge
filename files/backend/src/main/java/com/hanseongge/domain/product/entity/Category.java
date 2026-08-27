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

import java.time.OffsetDateTime;

/**
 * 상품 카테고리 ({@code categories}).
 *
 * <p>부모를 연관관계가 아니라 {@code parentId} 로 둔다. 자기참조 {@code @ManyToOne} 은
 * 조회 한 번이 계층 전체를 끌어오는 경로를 만들고, 카테고리는 2단계로 얕게 쓰기로
 * 확정되어 있어 그 비용을 치를 이유가 없다.
 */
@Entity
@Getter
@Table(name = "categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "sort", nullable = false)
    private int sort;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private Category(Long parentId, String name, int sort) {
        this.parentId = parentId;
        this.name = name;
        this.sort = sort;
        this.active = true;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public static Category create(Long parentId, String name, int sort) {
        return new Category(parentId, name, sort);
    }

    public void update(Long parentId, String name, int sort, boolean active) {
        this.parentId = parentId;
        this.name = name;
        this.sort = sort;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean isRoot() {
        return parentId == null;
    }
}
