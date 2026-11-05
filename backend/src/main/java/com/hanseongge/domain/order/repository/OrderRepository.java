package com.hanseongge.domain.order.repository;

import com.hanseongge.domain.order.entity.Order;
import com.hanseongge.domain.order.entity.OrderStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, OrderRepositoryCustom {

    Optional<Order> findByOrderNo(String orderNo);

    Page<Order> findByUserIdOrderByOrderedAtDescIdDesc(Long userId, Pageable pageable);

    /**
     * 내 주문 — 상태 묶음과 기간으로 거른다.
     *
     * <p>화면에서 거르지 않는다. 한 장(20건)만 받아 거르면 "결제 대기 3건" 이라고
     * 적힌 탭을 눌렀는데 목록이 비는 일이 생긴다 — 그 3건이 다음 장에 있기 때문이다.
     * 개수는 서버가 센 총계를 쓰면서 목록만 화면에서 거르면 반드시 어긋난다.
     *
     * @param statuses 비우면 전체. 묶음(결제 대기·준비 중·배송·취소)을 펼쳐서 넘긴다
     * @param from     이 시각부터. null 이면 처음부터
     * @param to       이 시각까지. null 이면 지금까지
     */
    @Query("""
            SELECT o FROM Order o
            WHERE o.userId = :userId
              AND (:#{#statuses == null || #statuses.isEmpty()} = true OR o.status IN :statuses)
              AND (CAST(:from AS timestamp) IS NULL OR o.orderedAt >= :from)
              AND (CAST(:to   AS timestamp) IS NULL OR o.orderedAt <  :to)
            ORDER BY o.orderedAt DESC, o.id DESC
            """)
    Page<Order> searchMine(@Param("userId") Long userId,
                           @Param("statuses") Collection<OrderStatus> statuses,
                           @Param("from") OffsetDateTime from,
                           @Param("to") OffsetDateTime to,
                           Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndStatusIn(Long userId, Collection<OrderStatus> statuses);

    /**
     * 결제 없이 방치된 주문. <b>결제수단마다 접는 시점이 다르다.</b>
     *
     * <h2>카드·간편결제 — 주문 시각 + 고정 시간</h2>
     * 결제창을 띄워 놓고 떠난 주문이다. 토스 결제창 유효시간이 10분이라 그보다
     * 넉넉히 잡는다({@code app.order.stale-minutes}).
     *
     * <h2>무통장입금 — 그 주문에 안내한 입금 기한</h2>
     * 고정 시간을 쓰지 않는다. 무통장은 사람이 은행에 가서 돈을 부치고 우리가 통장을
     * 보고 확인할 때까지 ORDER_CREATED 로 남아 있어, 카드와 비교가 안 되게 오래 걸린다.
     * 카드 기준으로 접으면 <b>고객이 안내받은 기한 안에 부쳤는데도 주문이 없어진다.</b>
     * 그 돈은 갈 곳이 없다 — 주문은 취소됐고 재고는 돌아갔는데 통장에는 돈이 찍혀 있다.
     *
     * <p>그래서 {@code deposit_due_at} 을 본다. 고객 화면에 적어 둔 바로 그 시각이다.
     * 화면에 적힌 기한과 실제로 접히는 시각이 같아야 설명할 수 있다.
     *
     * <p>접지 않고 두는 선택지도 있었지만 재고와 시세가 샌다. 재고는 주문서를 만들 때
     * 가져가므로 입금하지 않은 주문이 골드바를 계속 물고 있고, 시세 연동 상품은 주문
     * 시점 가격이 굳어 있어 무기한이면 「쌀 때 주문해 두고 오르면 입금한다」가 된다.
     *
     * <p>{@code deposit_due_at} 이 비어 있으면 접지 않는다. 언제까지인지 모르는 주문을
     * 짐작으로 접지 않는다 — 관리자가 주문 상세에서 직접 취소할 수 있다.
     *
     * <p>{@code SKIP LOCKED} 를 쓰지 않는다 — 지금은 단일 인스턴스라 스케줄러가 하나뿐이고,
     * 접는 동작 자체가 멱등이다(이미 취소된 주문은 다시 읽어 걸러낸다). 인스턴스를 늘릴 때
     * 다시 볼 것.
     *
     * @param cutoff 카드 주문 기준 — 이 시각 전에 만들어진 것
     * @param now    무통장 주문 기준 — 입금 기한이 이 시각보다 전인 것
     */
    @Query("""
            SELECT o FROM Order o
            WHERE o.status IN :statuses
              AND (
                    (o.paymentMethod = com.hanseongge.domain.order.entity.PaymentMethod.BANK_TRANSFER
                     AND o.depositDueAt IS NOT NULL AND o.depositDueAt < :now)
                 OR (o.paymentMethod <> com.hanseongge.domain.order.entity.PaymentMethod.BANK_TRANSFER
                     AND o.orderedAt < :cutoff)
                  )
            ORDER BY o.id ASC
            """)
    List<Order> findStale(@Param("statuses") Collection<OrderStatus> statuses,
                          @Param("cutoff") OffsetDateTime cutoff,
                          @Param("now") OffsetDateTime now,
                          Limit limit);

    /** 주문번호 채번용 — 오늘 만든 주문 수. {@code idx_order_user} 가 아니라 order_no 접두로 센다. */
    long countByOrderNoStartingWith(String prefix);
}
