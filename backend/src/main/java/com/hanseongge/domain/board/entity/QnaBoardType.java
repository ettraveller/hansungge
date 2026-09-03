package com.hanseongge.domain.board.entity;

/**
 * 문의가 속한 게시판.
 *
 * <p>문의게시판과 제품문의는 구조가 같아 한 테이블({@code qnas})을 쓰고 이 값으로만
 * 갈린다.
 *
 * <p><b>{@code product_id} 의 NULL 여부로 가르지 않는 이유</b> — 제품문의인데 상품을
 * 특정하지 못한 글("골드바 한 돈 얼마인가요")이 조용히 문의게시판으로 새어 들어간다.
 * 레퍼런스의 제품문의 게시판에도 그런 글이 실제로 있다. 소속은 소속대로 적는다.
 */
public enum QnaBoardType {

    /** 문의게시판 — 주문·배송·매입 등 전반 */
    GENERAL,

    /** 제품문의 — 상품에 관한 질문. 상품 연결은 선택이다 */
    PRODUCT
}
