package com.hanseongge.domain.cart.repository;

import com.hanseongge.domain.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    /** 담은 순서대로. 화면에서 줄이 튀지 않게 정렬을 고정한다. */
    List<CartItem> findByCartIdOrderByAddedAtAscIdAsc(Long cartId);

    Optional<CartItem> findByCartIdAndProductIdAndOptionId(Long cartId, Long productId, Long optionId);

    /**
     * 남의 장바구니 줄을 건드리지 못하게 <b>cart_id 까지 조건에 넣는다.</b>
     * id 만으로 찾으면 다른 사람의 itemId 를 넣었을 때 그대로 지워진다.
     */
    Optional<CartItem> findByIdAndCartId(Long id, Long cartId);

    long countByCartId(Long cartId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM CartItem c WHERE c.cartId = :cartId")
    int deleteAllByCartId(@Param("cartId") Long cartId);
}
