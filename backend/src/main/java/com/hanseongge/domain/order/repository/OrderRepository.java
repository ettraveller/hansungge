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
     * 결제 없이 방치된 주문. <b>무통장입금 주문은 제외한다.</b>
     *
     * <p>무통장은 결제창을 거치지 않아 사람이 돈을 부치고 우리가 통장을 보고 확인할
     * 때까지 ORDER_CREATED 로 남아 있다. 그 시간이 카드 결제와 비교가 안 되게 길다 —
     * 은행 영업시간, 이체 한도, 보안카드 재발급까지 걸리면 하루를 넘기기도 한다.
     *
     * <p>여기서 걸러내지 않으면 고객에게 「3시간 안에 입금하세요」라고 안내해 놓고
     * 주문을 10분 만에 접는다(운영 설정). 그 뒤에 들어온 돈은 갈 곳이 없다 —
     * 주문은 취소됐고 재고는 돌아갔는데 통장에는 돈이 찍혀 있다.
     *
     * <p>대가는 입금하지 않은 무통장 주문이 재고를 계속 붙들고 있는 것이다. 자동으로
     * 풀지 않는 쪽을 택했다. 입금하려는 사이에 취소되는 쪽이 훨씬 나쁘고, 관리자가
     * 주문 상세에서 직접 취소할 수 있다.
     *
     * <p>{@code SKIP LOCKED} 를 쓰지 않는다 — 지금은 단일 인스턴스라 스케줄러가 하나뿐이고,
     * 접는 동작 자체가 멱등이다(이미 취소된 주문은 다시 읽어 걸러낸다). 인스턴스를 늘릴 때
     * 다시 볼 것.
     */
    @Query("""
            SELECT o FROM Order o
            WHERE o.status IN :statuses AND o.orderedAt < :cutoff
              AND o.paymentMethod <> com.hanseongge.domain.order.entity.PaymentMethod.BANK_TRANSFER
            ORDER BY o.id ASC
            """)
    List<Order> findStale(@Param("statuses") Collection<OrderStatus> statuses,
                          @Param("cutoff") OffsetDateTime cutoff,
                          Limit limit);

    /** 주문번호 채번용 — 오늘 만든 주문 수. {@code idx_order_user} 가 아니라 order_no 접두로 센다. */
    long countByOrderNoStartingWith(String prefix);
}
