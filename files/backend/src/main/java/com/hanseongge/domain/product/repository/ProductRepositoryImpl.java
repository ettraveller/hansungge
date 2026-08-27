package com.hanseongge.domain.product.repository;

import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductStatus;
import com.hanseongge.domain.product.entity.QProduct;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

/**
 * 상품 동적 검색 (QueryDSL).
 *
 * <p>이 클래스는 <b>해석하지 않는다.</b> 카테고리 하위 펼치기와 노출 범위 결정은 서비스가
 * 끝내고 {@link ProductQuery} 로 넘겨준다. 저장소가 "공개용인가"를 판단하기 시작하면
 * 그 규칙이 서비스와 저장소 두 곳에 생긴다.
 */
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    /** 고객에게 보여도 되는 상태 (판매중 / 품절). */
    private static final List<ProductStatus> VISIBLE_STATUSES =
            List.of(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT);

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Product> search(ProductQuery query, Pageable pageable) {
        QProduct product = QProduct.product;
        BooleanBuilder where = buildWhere(query, product);

        List<Product> content = queryFactory
                .selectFrom(product)
                .where(where)
                // 최신 등록순 고정. 정렬을 클라이언트가 정하게 두면 인덱스를 벗어난 정렬로
                // 전건 스캔이 나고, 상품 목록은 그 비용을 감당할 만큼 작지 않다.
                .orderBy(product.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(product.count())
                .from(product)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public long countByCategory(Long categoryId, List<ProductStatus> excludedStatuses) {
        QProduct product = QProduct.product;
        BooleanBuilder where = new BooleanBuilder(product.categoryId.eq(categoryId));
        if (excludedStatuses != null && !excludedStatuses.isEmpty()) {
            where.and(product.status.notIn(excludedStatuses));
        }
        Long count = queryFactory.select(product.count()).from(product).where(where).fetchOne();
        return count == null ? 0L : count;
    }

    private BooleanBuilder buildWhere(ProductQuery query, QProduct product) {
        BooleanBuilder where = new BooleanBuilder();

        if (query.visibleOnly()) {
            where.and(product.active.isTrue());
            where.and(product.status.in(VISIBLE_STATUSES));
        }
        if (query.hasCategories()) {
            // 이미 하위 카테고리까지 펼쳐진 목록이다.
            where.and(product.categoryId.in(query.categoryIds()));
        }
        if (query.hasKeyword()) {
            where.and(product.name.containsIgnoreCase(query.keyword().trim()));
        }
        if (query.metalType() != null) {
            where.and(product.metalType.eq(query.metalType()));
        }
        if (query.priceType() != null) {
            where.and(product.priceType.eq(query.priceType()));
        }
        // 아래 두 조건은 관리자 전용이다. 공개 조회에서는 서비스가 null 로 지워서 넘긴다.
        if (query.status() != null) {
            where.and(product.status.eq(query.status()));
        }
        if (query.active() != null) {
            where.and(product.active.eq(query.active()));
        }
        return where;
    }
}
