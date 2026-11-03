package com.hanseongge.domain.order.entity;

/**
 * 고객이 고른 결제수단 ({@code orders.payment_method} CHECK 와 같은 값이어야 한다).
 *
 * <h2>왜 주문에 두는가</h2>
 * {@code payments} 테이블에도 수단이 있지만 그것은 <b>PG 가 승인 응답에 실어 준 값</b>
 * 이다 — 카드인지 계좌이체인지는 고객이 결제창 안에서 고르므로 승인이 끝나야 안다.
 * 여기 있는 값은 그 전, 주문서를 만드는 시점에 <b>고객이 어느 길로 갈지</b> 고른 것이다.
 *
 * <p>무통장 주문에는 애초에 PG 가 끼지 않아 {@code payments} 행이 승인 전까지 없다.
 * 주문에 두지 않으면 "입금을 기다리는 주문"을 상태만으로 가려낼 수 없다.
 *
 * @see com.hanseongge.domain.product.entity.Product#isCardPayable()
 */
public enum PaymentMethod {

    /** 카드·간편결제 — 토스페이먼츠 결제위젯을 띄운다 */
    EASY_PAY("간편결제"),

    /**
     * 무통장입금 — 계좌로 직접 받는다.
     *
     * <p>순금·순은·골드바·24K·시세 연동 상품은 이 길뿐이다. 카드사가 온라인 거래를
     * 제한하는 품목이라 PG 심사를 통과하지 못한다 (2026-11-03 토스페이먼츠 회신).
     */
    BANK_TRANSFER("무통장입금");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isBankTransfer() {
        return this == BANK_TRANSFER;
    }
}
