package com.hanseongge.domain.marketprice.repository;

import com.hanseongge.domain.marketprice.entity.MarketPriceRaw;
import com.hanseongge.domain.marketprice.entity.MetalType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
