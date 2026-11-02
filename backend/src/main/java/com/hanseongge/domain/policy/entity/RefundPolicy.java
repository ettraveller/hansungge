package com.hanseongge.domain.policy.entity;

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
 * 환불규정의 한 판 ({@code refund_policies}, V34).
 *
 * <p>결제 직전에 고객이 읽고 동의하는 문서다. <b>수정 메서드가 없다</b> — 개정은
 * 새 판을 쌓는 것이고, 이미 쌓인 판은 어느 주문의 "동의한 그 내용"일 수 있어
 * 고치면 안 된다. DB 트리거({@code trg_refund_policy_guard})가 UPDATE/DELETE 를
 * 한 번 더 막는다 (코딩규칙 P2 와 같은 구조).
 */
@Entity
@Getter
@Table(name = "refund_policies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "content", nullable = false)
    private String content;

    /** 무엇을 왜 고쳤는가. 감사로그에도 함께 남는다. */
    @Column(name = "change_note", length = 200, nullable = false)
    private String changeNote;

    @Column(name = "created_by", length = 60, nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private RefundPolicy(String content, String changeNote, String createdBy) {
        this.content = content;
        this.changeNote = changeNote;
        this.createdBy = createdBy;
        this.createdAt = OffsetDateTime.now();
    }

    /** 새 판 발행. 기존 판을 고치는 길은 없다. */
    public static RefundPolicy publish(String content, String changeNote, String createdBy) {
        return new RefundPolicy(content, changeNote, createdBy);
    }
}
