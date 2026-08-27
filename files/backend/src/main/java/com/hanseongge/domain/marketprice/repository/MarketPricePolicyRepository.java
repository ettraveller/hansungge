package com.hanseongge.domain.marketprice.repository;

import com.hanseongge.domain.marketprice.entity.MarketPricePolicy;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MarketPricePolicyRepository extends JpaRepository<MarketPricePolicy, Long> {

    Optional<MarketPricePolicy> findByMetalTypeAndPriceSide(MetalType metalType, PriceSide priceSide);

    List<MarketPricePolicy> findAllByOrderByMetalTypeAscPriceSideAsc();
}
