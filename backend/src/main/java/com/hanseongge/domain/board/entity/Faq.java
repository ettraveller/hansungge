package com.hanseongge.domain.board.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자주 묻는 질문 한 건 (표는 V5 부터 있었고, 화면은 2026-11-24 에 붙였다).
 *
 * <p>답은 <b>글자 그대로</b> 내보낸다. HTML 을 받지 않는다 — 관리자가 붙여 넣은
 * 태그가 손님 화면에서 그대로 살아나는 길을 두지 않는다. 줄바꿈은 화면이 살린다.
 */
@Entity
@Table(name = "faqs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 묶음 이름. 주문·배송·결제·매입처럼 관리자가 적는다 */
    @Column(length = 50, nullable = false)
    private String category;

    @Column(length = 300, nullable = false)
    private String question;

    @Column(nullable = false, columnDefinition = "text")
    private String answer;

    @Column(nullable = false)
    private int sort;

    @Column(nullable = false)
    private boolean active;

    private Faq(String category, String question, String answer, int sort, boolean active) {
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.sort = sort;
        this.active = active;
    }

    public static Faq of(String category, String question, String answer, int sort, boolean active) {
        return new Faq(category, question, answer, sort, active);
    }
}
