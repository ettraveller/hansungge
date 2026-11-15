package com.hanseongge.domain.banner.repository;

import com.hanseongge.domain.banner.entity.HeroBanner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HeroBannerRepository extends JpaRepository<HeroBanner, Long> {

    /** 손님에게 나갈 차례. 같은 자리면 먼저 만든 것이 앞이다. */
    List<HeroBanner> findByActiveTrueOrderBySortAscIdAsc();

    /** 관리자 화면용. 꺼 둔 것까지 같은 차례로 본다. */
    List<HeroBanner> findAllByOrderBySortAscIdAsc();
}
