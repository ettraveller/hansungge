package com.hanseongge.domain.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 감사로그 ({@code audit_logs}, 설계서 §3.5).
 *
 * <p><b>append-only 다.</b> 수정·삭제 메서드를 두지 않는다. 감사로그를 고칠 수 있으면
 * 감사로그가 아니다. {@link com.hanseongge.global.audit.BaseTimeEntity} 를 상속하지 않는
 * 이유도 같다 — {@code updated_at} 컬럼의 존재 자체가 잘못된 신호다.
 *
 * <p>{@code before_value} / {@code after_value} 는 JSONB 다. 감사 대상이 시세 정책부터
 * 관리자 계정까지 제각각이라 컬럼을 고정할 수 없고, 나중에 "무엇이 어떻게 바뀌었나"를
 * 되짚으려면 변경 전후를 통째로 들고 있어야 한다.
 */
@Entity
@Getter
@Table(name = "audit_logs")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", length = 10, nullable = false)
    private ActorType actorType;

    /** 관리자 login_id 또는 'SYSTEM'. */
    @Column(name = "actor", length = 100, nullable = false)
    private String actor;

    /** ADMIN_LOGIN, ADMIN_CREATE, PRICE_APPLY ... */
    @Column(name = "action", length = 60, nullable = false)
    private String action;

    @Column(name = "target_type", length = 60)
    private String targetType;

    @Column(name = "target_id", length = 60)
    private String targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value")
    private String beforeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value")
    private String afterValue;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private AuditLog(ActorType actorType, String actor, String action,
                     String targetType, String targetId,
                     String beforeValue, String afterValue,
                     String ip, String userAgent) {
        this.actorType = actorType;
        this.actor = actor;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.ip = ip;
        this.userAgent = userAgent;
        this.createdAt = OffsetDateTime.now();
    }

    public static AuditLog of(ActorType actorType, String actor, String action,
                              String targetType, String targetId,
                              String beforeValue, String afterValue,
                              String ip, String userAgent) {
        return new AuditLog(actorType, actor, action, targetType, targetId,
                beforeValue, afterValue, ip, userAgent);
    }
}
