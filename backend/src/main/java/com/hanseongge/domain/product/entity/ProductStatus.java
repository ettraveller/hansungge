package com.hanseongge.domain.product.entity;

/** 상품 판매 상태. V3 마이그레이션의 CHECK 제약과 값이 일치해야 한다. */
public enum ProductStatus {

    ON_SALE("판매중"),
    SOLD_OUT("품절"),
    /** 노출하지 않음. 링크를 직접 알아도 보이지 않아야 한다 */
    HIDDEN("숨김"),
    DISCONTINUED("판매종료");

    private final String displayName;

    ProductStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** 고객에게 노출해도 되는 상태인가. */
    public boolean isVisible() {
        return this == ON_SALE || this == SOLD_OUT;
    }
}
