package com.hanseongge.domain.interest.repository;

import com.hanseongge.domain.interest.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    List<WishlistItem> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    long countByUserId(Long userId);

    /** 목록 화면이 상품마다 한 번씩 묻지 않도록, 보고 있는 상품들의 찜 여부를 한 번에 가져온다. */
    List<WishlistItem> findByUserIdAndProductIdIn(Long userId, Collection<Long> productIds);

    void deleteByUserIdAndProductId(Long userId, Long productId);
}
