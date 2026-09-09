package com.hanseongge.domain.marketprice.repository;

import com.hanseongge.domain.marketprice.entity.MarketPriceRaw;
import com.hanseongge.domain.marketprice.entity.MetalType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * L1 원본 저장소.
 *
 * <p>수정·삭제 메서드를 제공하지 않는다. 원본은 append-only 이고, 유일한 상태 변경은
 * 엔티티의 {@code markAnomaly()} 뿐이다.
 */
public interface MarketPriceRawRepository extends JpaRepository<MarketPriceRaw, Long> {

    Optional<MarketPriceRaw> findFirstByMetalTypeOrderByCollectedAtDescIdDesc(MetalType metalType);

    List<MarketPriceRaw> findByMetalTypeAndCollectedAtGreaterThanEqualOrderByCollectedAtAsc(
            MetalType metalType, OffsetDateTime from);

    Page<MarketPriceRaw> findByMetalTypeOrderByCollectedAtDescIdDesc(MetalType metalType, Pageable pageable);

    /**
     * 이미 받아 둔 고시인가. {@code ux_mpr_dedup} 과 같은 키다.
     *
     * <p>제약 위반을 잡는 대신 <b>미리 확인한다.</b> {@code REQUIRES_NEW} 안에서 제약
     * 위반이 나면 그 트랜잭션은 이미 rollback-only 로 표시되고, 예외를 잡아도 표시는
     * 지워지지 않아 커밋 시점에 {@code UnexpectedRollbackException} 으로 다시 터진다.
     * "잡았으니 괜찮다"고 보이지만 실제로는 아닌, 찾기 어려운 종류의 버그다.
     *
     * <p>유니크 인덱스는 그대로 둔다 — 이 확인은 흔한 경로를 위한 것이고, 인덱스는
     * 동시 수집이라는 드문 경로를 위한 것이다. 둘은 대체재가 아니다.
     */
    /**
     * 그 금속·그 쪽의 <b>가장 최근 원본</b>. 보정값을 바꾼 뒤 지금 시세에 다시 적용할 때 쓴다.
     *
     * <p>{@code priceSide} 가 null 인 행(양측 공통 기준가)도 후보다. 한쪽 전용 원본이
     * 없던 시절의 데이터가 그렇고, 수동 고시도 그렇게 쌓인다.
     */
    @Query("""
            SELECT r FROM MarketPriceRaw r
            WHERE r.metalType = :metalType
              AND (r.priceSide = :priceSide OR r.priceSide IS NULL)
            ORDER BY r.quoteAt DESC, r.id DESC
            """)
    List<MarketPriceRaw> findLatestForSide(@Param("metalType") MetalType metalType,
                                           @Param("priceSide") PriceSide priceSide,
                                           Pageable pageable);

    boolean existsBySourceIdAndMetalTypeAndPriceSideAndQuoteAt(
            Long sourceId, MetalType metalType,
            com.hanseongge.domain.marketprice.entity.PriceSide priceSide, OffsetDateTime quoteAt);
}
