package com.hanseongge.domain.marketprice.repository;

import com.hanseongge.domain.marketprice.entity.MarketPriceSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MarketPriceSourceRepository extends JpaRepository<MarketPriceSource, Long> {

    Optional<MarketPriceSource> findByCode(String code);
}
