package com.hanseongge.domain.marketprice.repository;

import com.hanseongge.domain.marketprice.entity.FetchResult;
import com.hanseongge.domain.marketprice.entity.MarketPriceFetchLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 수집 이력 저장소. append-only 이므로 조회와 저장만 쓴다.
 */
public interface MarketPriceFetchLogRepository extends JpaRepository<MarketPriceFetchLog, Long> {

    /** 대시보드의 "마지막 성공 시각". */
    Optional<MarketPriceFetchLog> findFirstByResultOrderByStartedAtDescIdDesc(FetchResult result);

    Optional<MarketPriceFetchLog> findFirstBySourceIdAndResultOrderByStartedAtDescIdDesc(
            Long sourceId, FetchResult result);

    /** 가장 최근 시도. 성공·실패 무관 — "언제 마지막으로 시도했나"에 답한다. */
    Optional<MarketPriceFetchLog> findFirstByOrderByStartedAtDescIdDesc();

    Page<MarketPriceFetchLog> findAllByOrderByStartedAtDescIdDesc(Pageable pageable);

    long countByResult(FetchResult result);
}
