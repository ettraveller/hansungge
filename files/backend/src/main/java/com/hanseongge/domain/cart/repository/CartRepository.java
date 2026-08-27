package com.hanseongge.domain.cart.repository;

import com.hanseongge.domain.cart.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    /** 회원당 하나뿐이다 ({@code carts.user_id UNIQUE}). */
    Optional<Cart> findByUserId(Long userId);
}
