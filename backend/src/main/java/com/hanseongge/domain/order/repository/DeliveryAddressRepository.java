package com.hanseongge.domain.order.repository;

import com.hanseongge.domain.order.entity.DeliveryAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeliveryAddressRepository extends JpaRepository<DeliveryAddress, Long> {

    /** 기본 배송지가 맨 위. 그다음은 최근에 넣은 순. */
    List<DeliveryAddress> findByUserIdOrderByIsDefaultDescCreatedAtDescIdDesc(Long userId);

    Optional<DeliveryAddress> findByUserIdAndIsDefaultTrue(Long userId);

    long countByUserId(Long userId);

    /**
     * 기본 배송지를 내린다.
     *
     * <p>한 줄씩 불러와 고치지 않고 벌크로 지우는 이유 — 새 기본을 올리기 <b>전에</b>
     * 반드시 내려가 있어야 한다. 부분 UNIQUE 인덱스가 걸려 있어 둘이 겹치면 실패한다.
     * {@code flushAutomatically} 로 호출 시점에 쿼리가 나가게 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DeliveryAddress a SET a.isDefault = false WHERE a.userId = :userId AND a.isDefault = true")
    void clearDefault(@Param("userId") Long userId);
}
