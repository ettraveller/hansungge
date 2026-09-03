package com.hanseongge.domain.admin.entity;

/**
 * 관리자 권한 등급 (설계서 §7.4).
 *
 * <p>시세 적용 권한을 {@code ADMIN} 이상으로 제한하는 것이 핵심이다 —
 * 가격에 직결되는 유일한 조작이기 때문이다.
 */
public enum AdminRole {

    /** 전체 + 관리자 계정 관리 + 시스템 설정 */
    SUPER_ADMIN,
    /** 시세 적용, 상품, 주문, 회원, 콘텐츠 */
    ADMIN,
    /** 상품, 주문, 배송, 콘텐츠 (시세 적용 불가) */
    MANAGER,
    /** 주문·배송 조회/처리, 콘텐츠 (읽기 위주) */
    OPERATOR;

    /** 시세 최종 적용 권한 보유 여부. */
    public boolean canApplyMarketPrice() {
        return this == SUPER_ADMIN || this == ADMIN;
    }
}
