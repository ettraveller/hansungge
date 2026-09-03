package com.hanseongge.domain.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 장바구니 ({@code carts}).
 *
 * <p>회원당 하나뿐이다({@code user_id UNIQUE}). 비회원 장바구니는 만들지 않는다 —
 * 브라우저에만 있는 장바구니는 기기를 바꾸면 사라지고, 서버에 두면 익명 데이터를
 * 무한정 보관하게 된다. 담으려면 로그인해야 한다.
 *
 * <p>이 엔티티에는 <b>금액이 없다.</b> 합계는 조회 시점에 현재 시세로 다시 계산한다
 * (개발계획 §13.6) — 저장해 두면 시세가 바뀐 뒤에도 옛 합계를 보여주게 된다.
 */
@Entity
@Getter
@Table(name = "carts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private Cart(Long userId) {
        this.userId = userId;
        this.updatedAt = OffsetDateTime.now();
    }

    public static Cart createFor(Long userId) {
        return new Cart(userId);
    }

    /** 담기·수량변경·삭제 때 호출한다. "언제까지 만지던 장바구니인가"를 남긴다. */
    public void touch() {
        this.updatedAt = OffsetDateTime.now();
    }
}
