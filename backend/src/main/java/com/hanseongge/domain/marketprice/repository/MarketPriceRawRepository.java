package com.hanseongge.domain.marketprice.repository;

import com.hanseongge.domain.marketprice.entity.MarketPriceRaw;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * L1 원본 저장소.
 *
 * <p>쓰기는 INSERT 뿐이다. 원본을 고치거나 지우는 메서드를 두지 않는다 — 누가 언제
 * 무엇을 입력했는지가 이 테이블이 존재하는 이유다.
 */
public interface MarketPriceRawRepository extends JpaRepository<MarketPriceRaw, Long> {
}
