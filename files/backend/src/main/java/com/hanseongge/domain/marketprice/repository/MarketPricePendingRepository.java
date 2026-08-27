package com.hanseongge.domain.marketprice.repository;

import com.hanseongge.domain.marketprice.entity.MarketPricePending;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PendingStatus;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketPricePendingRepository extends JpaRepository<MarketPricePending, Long> {

    Page<MarketPricePending> findByStatusOrderByCreatedAtDescIdDesc(PendingStatus status, Pageable pageable);

    List<MarketPricePending> findByStatusOrderByCreatedAtDescIdDesc(PendingStatus status);

    /**
     * metal×side 당 미처리 승인건.
     *
     * <p>V2 에는 이를 강제하는 부분 UNIQUE 인덱스가 없어(V6/V7 보강 항목) 서비스가
     * 이 조회로 중복 등록을 막는다. 응용 수준 방어이므로 동시 요청에서는 두 건이 생길 수 있다.
     */
    Optional<MarketPricePending> findFirstByMetalTypeAndPriceSideAndStatusOrderByCreatedAtDesc(
            MetalType metalType, PriceSide priceSide, PendingStatus status);

    long countByStatus(PendingStatus status);
}
