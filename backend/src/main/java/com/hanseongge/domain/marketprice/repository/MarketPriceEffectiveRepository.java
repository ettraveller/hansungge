package com.hanseongge.domain.marketprice.repository;

import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * L3 최종 시세 저장소.
 *
 * <p>DELETE 는 DB 트리거가 막는다. 그래서 여기에도 삭제 경로를 두지 않는다 —
 * 호출하면 런타임에 터질 메서드를 API 로 노출할 이유가 없다.
 */
public interface MarketPriceEffectiveRepository extends JpaRepository<MarketPriceEffective, Long> {

    /** 현재 적용값. metal×side 당 1건임을 부분 UNIQUE 인덱스가 보장한다. */
    Optional<MarketPriceEffective> findByMetalTypeAndPriceSideAndEffectiveToIsNull(
            MetalType metalType, PriceSide priceSide);

    /** 전 금속×side 의 현재값. 관리자 대시보드·공개 시세표에서 한 번에 읽는다. */
    List<MarketPriceEffective> findByEffectiveToIsNullOrderByMetalTypeAscPriceSideAsc();

    Page<MarketPriceEffective> findByMetalTypeAndPriceSideOrderByEffectiveFromDescIdDesc(
            MetalType metalType, PriceSide priceSide, Pageable pageable);

    List<MarketPriceEffective> findByMetalTypeAndPriceSideAndEffectiveFromGreaterThanEqualOrderByEffectiveFromAsc(
            MetalType metalType, PriceSide priceSide, OffsetDateTime from);

    /**
     * metal×side 별 <b>직전</b> 최종 시세 (마감된 행 중 가장 최근).
     *
     * <p>공개 시세표의 변동폭을 계산하는 근거다. 이 테이블이 append-only 라서 별도 이력
     * 테이블 없이 <b>마감된 행이 그대로 과거값</b>이 된다 (설계서 §12.2 L3).
     *
     * <p>전 조합을 한 번에 읽는다. metal×side 마다 따로 부르면 시세표 한 번에 10 쿼리가
     * 나가고, 그 비용이 변동폭 하나 때문에 매 요청 발생한다.
     *
     * <h2>{@code DISTINCT ON} 을 쓰는 이유</h2>
     * 원래 이 자리에는 <b>상관 서브쿼리</b>가 있었다 —
     * {@code effectiveTo = (SELECT MAX(...) WHERE prev.metalType = e.metalType ...)}.
     * 읽기는 좋지만 PostgreSQL 이 그 서브쿼리를 <b>바깥 행마다 한 번씩</b> 실행한다.
     * 이력이 11,000 행일 때 {@code loops=10970}, 버퍼 507,485 회, <b>4.1초</b>였다.
     * 결과는 10 줄인데. 이력은 고시할 때마다 쌓이기만 하므로 시간이 갈수록 느려진다.
     *
     * <p>{@code DISTINCT ON} 은 한 번 훑고 정렬해서 조합별 첫 줄만 남긴다 — 같은 데이터에서
     * <b>23ms</b>. 표준 SQL 이 아니지만 이 프로젝트는 이미 PostgreSQL 에 묶여 있다
     * (부분 UNIQUE 인덱스 {@code ux_mpe_current}, append-only 트리거, advisory lock, jsonb).
     *
     * <p>정렬 기준은 {@code effective_to} 다. "직전"의 뜻이 그것이라서다. 이 테이블이
     * append-only 체인이라 {@code effective_from} 순서와 결과가 같지만, 인덱스에 맞추려고
     * 뜻이 다른 컬럼으로 바꾸지는 않는다 — 11,000 행 기준 두 계획의 차이는 4ms 다.
     */
    @Query(value = """
            SELECT DISTINCT ON (metal_type, price_side) *
            FROM market_prices_effective
            WHERE effective_to IS NOT NULL
            ORDER BY metal_type, price_side, effective_to DESC
            """, nativeQuery = true)
    List<MarketPriceEffective> findLatestClosedPerMetalSide();

    /**
     * {@code (metal, side)} 단위 배타 잠금 (개발계획 §12.6).
     *
     * <p>트랜잭션 종료와 함께 자동 해제되는 advisory lock 이다. 잠글 <b>대상 행이 아직
     * 없을 수도</b> 있어서(최초 발행) 행 잠금(SELECT FOR UPDATE)으로는 동시성을 막을 수
     * 없다. 그래서 행이 아니라 <b>키 문자열</b>을 잠근다.
     *
     * <p>이 락이 없어도 부분 UNIQUE 인덱스가 최종 안전망이라 데이터는 깨지지 않는다.
     * 다만 그때는 한쪽이 제약 위반으로 실패하므로, 실패 대신 대기시키기 위해 락을 쓴다.
     */
    @Query(value = "SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext(:lockKey))) AS acquired",
           nativeQuery = true)
    Integer acquireLock(@Param("lockKey") String lockKey);
}
