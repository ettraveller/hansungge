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
