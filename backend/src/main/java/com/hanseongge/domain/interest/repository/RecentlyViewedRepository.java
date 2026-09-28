package com.hanseongge.domain.interest.repository;

import com.hanseongge.domain.interest.entity.RecentlyViewed;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RecentlyViewedRepository extends JpaRepository<RecentlyViewed, Long> {

    List<RecentlyViewed> findByUserIdOrderByViewedAtDescIdDesc(Long userId, Pageable pageable);

    Optional<RecentlyViewed> findByUserIdAndProductId(Long userId, Long productId);

    /** 상한을 넘은 오래된 줄을 골라낸다. 이 표는 그냥 두면 끝없이 자란다. */
    List<RecentlyViewed> findByUserIdOrderByViewedAtAscIdAsc(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    void deleteByUserId(Long userId);
}
