package com.hanseongge.domain.product.entity;

/**
 * 상품 이미지의 갈래. V31 의 {@code chk_pi_kind} 와 값이 일치해야 한다.
 *
 * <p>성격이 전혀 다른 두 종류라 나눠 둔다. 섞으면 어느 쪽으로 그려도 한쪽이 깨진다.
 */
public enum ProductImageKind {

    /**
     * 상품 사진. 상세 상단에서 썸네일로 넘겨 본다.
     *
     * <p><b>첫 장이 대표다.</b> 목록·검색결과·장바구니·주문서가 쓰는 한 장이며,
     * {@code products.thumbnail_key} 에 사본이 들어간다.
     */
    GALLERY("대표 이미지"),

    /**
     * 상세페이지. 설명 아래에 올린 순서대로 전체 폭으로 쌓는다.
     *
     * <p>860×9531 처럼 세로로 길다. 정사각형 썸네일에 넣으면 맨 위 한 조각만 보인다.
     */
    DETAIL("상세 이미지");

    private final String displayName;

    ProductImageKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
