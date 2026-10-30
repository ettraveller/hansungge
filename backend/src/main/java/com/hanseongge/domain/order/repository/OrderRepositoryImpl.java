package com.hanseongge.domain.order.repository;

import com.hanseongge.domain.order.dto.AdminOrderSearchCondition;
import com.hanseongge.domain.order.entity.Order;
import com.hanseongge.domain.order.entity.OrderStatus;
import com.hanseongge.domain.order.entity.QOrder;
import com.hanseongge.domain.shipment.entity.QShipment;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 관리자 주문 검색 (QueryDSL).
 *
 * <p>「발송 대기」의 뜻을 여기 한 곳에만 둔다 — <b>결제완료 또는 준비중이면서 송장번호가
 * 없는 주문</b>이다. 화면이나 서비스가 각자 판단하면 목록의 줄 수와 상단의 건수가
 * 어긋나는 날이 온다.
 */
@RequiredArgsConstructor
public class OrderRepositoryImpl implements OrderRepositoryCustom {

    /** 아직 보내지 않은, 보내야 하는 주문의 상태. */
    private static final List<OrderStatus> UNSHIPPED =
            List.of(OrderStatus.PAID, OrderStatus.PREPARING);

    /**
     * 주문일 범위를 해석할 시간대.
     *
     * <p>관리자가 "10월 1일부터"라고 할 때 그것은 한국 시간 00:00 이다. 저장은
     * {@code TIMESTAMPTZ} 라 UTC 기준으로 비교되므로, 날짜를 그대로 쓰면 아홉 시간이
     * 밀려 전날 주문이 섞여 들어온다.
     */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Order> searchForAdmin(AdminOrderSearchCondition condition, Pageable pageable) {
        QOrder order = QOrder.order;
        BooleanBuilder where = buildWhere(condition, order);

        List<Order> content = queryFactory
                .selectFrom(order)
                .where(where)
                // 최신순 고정. 관리자가 보는 것은 늘 "방금 들어온 주문"이다.
                .orderBy(order.orderedAt.desc(), order.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> countQuery = queryFactory
                .select(order.count())
                .from(order)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public long countAwaitingShipment() {
        QOrder order = QOrder.order;
        Long count = queryFactory
                .select(order.count())
                .from(order)
                .where(awaitingShipment(order))
                .fetchOne();
        return count == null ? 0 : count;
    }

    private BooleanBuilder buildWhere(AdminOrderSearchCondition c, QOrder order) {
        BooleanBuilder where = new BooleanBuilder();

        if (c.status() != null) {
            where.and(order.status.eq(c.status()));
        }
        if (c.wantsAwaitingOnly()) {
            where.and(awaitingShipment(order));
        }
        if (c.keyword() != null) {
            // 주문번호는 대문자 고정이라 그대로 비교해도 되지만, 받는 분 이름과 한 칸에서
            // 받으므로 둘 다 대소문자를 가리지 않는다.
            where.and(order.orderNo.containsIgnoreCase(c.keyword())
                    .or(order.receiverName.containsIgnoreCase(c.keyword())));
        }
        if (c.from() != null) {
            where.and(order.orderedAt.goe(c.from().atStartOfDay(KST).toOffsetDateTime()));
        }
        if (c.to() != null) {
            // 끝 날짜를 포함한다. 관리자가 "10월 31일까지"라고 하면 그날 23:59 주문도 든다.
            where.and(order.orderedAt.loe(c.to().atTime(LocalTime.MAX).atZone(KST).toOffsetDateTime()));
        }
        return where;
    }

    /**
     * 보내야 하는데 아직 송장번호가 없는 주문.
     *
     * <p>{@code NOT EXISTS} 로 쓴다. 왼쪽 조인으로 쓰면 배송 행이 없는 주문과 있는데
     * 번호만 빈 주문을 한 조건으로 묶기 어렵고, 페이지 수를 세는 쿼리에서 중복이 생긴다.
     */
    private com.querydsl.core.types.dsl.BooleanExpression awaitingShipment(QOrder order) {
        QShipment shipment = QShipment.shipment;
        return order.status.in(UNSHIPPED)
                .and(JPAExpressions.selectOne()
                        .from(shipment)
                        .where(shipment.orderId.eq(order.id)
                                .and(shipment.trackingNo.isNotNull())
                                .and(shipment.trackingNo.ne("")))
                        .notExists());
    }
}
