package com.hanseongge.domain.payment.repository;

import com.hanseongge.domain.payment.entity.Payment;
import com.hanseongge.domain.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderIdOrderByIdDesc(Long orderId);

    Optional<Payment> findByPgTid(String pgTid);

    /**
     * 관리자 결제 내역 — [Payment, 주문번호] 행, 최신 시도부터.
     *
     * <p>주문번호를 함께 끌어온다. 줄마다 주문을 따로 조회하면 목록 한 장에 스무 번
     * 질의가 나가고, 화면이 id 만 보여주면 사람이 PG 대시보드와 대조할 수가 없다 —
     * 전화로 읽는 것도, 토스 주문번호 검색에 넣는 것도 전부 주문번호다.
     *
     * <p>검색어의 CAST 는 PostgreSQL 때문이다 — null 파라미터의 타입을 추론하지 못해
     * bytea 로 보고, {@code varchar LIKE bytea} 연산자가 없다며 전체 조회까지 깨진다.
     */
    @Query("""
            SELECT p, o.orderNo FROM Payment p
            JOIN Order o ON o.id = p.orderId
            WHERE (:status IS NULL OR p.status = :status)
              AND (:orderNo IS NULL
                   OR o.orderNo LIKE CONCAT('%', CAST(:orderNo AS string), '%'))
            ORDER BY p.id DESC
            """)
    Page<Object[]> searchForAdmin(@Param("status") PaymentStatus status,
                                  @Param("orderNo") String orderNo,
                                  Pageable pageable);
}
