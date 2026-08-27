package com.hanseongge.domain.user.entity;

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

import java.time.OffsetDateTime;

/**
 * 약관 동의 이력 ({@code user_agreements}, V8 / 결함 D-9).
 *
 * <p><b>append-only 다.</b> 동의를 철회해도 기존 행을 고치지 않고 {@code agreed=false} 행을
 * 새로 넣는다. "언제 동의했고 언제 철회했는가"가 둘 다 남아야 증명이 되기 때문이고,
 * DB 트리거({@code trg_user_agreement_guard})가 UPDATE·DELETE 를 거부한다.
 *
 * <p>그래서 이 클래스에는 상태를 바꾸는 메서드가 없다.
 */
@Entity
@Getter
@Table(name = "user_agreements")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type", length = 30, nullable = false)
    private AgreementType agreementType;

    /**
     * 동의한 약관의 버전.
     *
     * <p>약관을 개정하면 이전 동의는 그 시점 문서에 대한 것이므로, 버전을 남기지 않으면
     * "무엇에 동의했는가"를 특정할 수 없다.
     */
    @Column(name = "version", length = 20, nullable = false)
    private String version;

    @Column(name = "agreed", nullable = false)
    private boolean agreed;

    @Column(name = "agreed_at", nullable = false)
    private OffsetDateTime agreedAt;

    /** 동의 시점 IP. 분쟁 시 근거가 된다. */
    @Column(name = "ip", length = 45)
    private String ip;

    private UserAgreement(Long userId, AgreementType type, String version, boolean agreed, String ip) {
        this.userId = userId;
        this.agreementType = type;
        this.version = version;
        this.agreed = agreed;
        this.agreedAt = OffsetDateTime.now();
        this.ip = ip;
    }

    public static UserAgreement record(Long userId, AgreementType type, String version,
                                       boolean agreed, String ip) {
        return new UserAgreement(userId, type, version, agreed, ip);
    }
}
