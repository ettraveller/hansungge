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

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 휴대폰 본인확인 이력 ({@code identity_verifications}).
 *
 * <h2>가입 전에도 존재한다</h2>
 * 본인확인은 회원이 되기 <b>전에</b> 일어난다. 그래서 {@link #userId} 가 null 인 행이
 * 정상적으로 있고, 가입이 끝나는 순간 {@link #linkTo} 로 연결된다.
 *
 * <h2>신원 컬럼은 고칠 수 없다</h2>
 * DB 트리거({@code trg_identity_verification_guard})가 신원 컬럼의 UPDATE 와 모든 DELETE 를
 * 거부한다. "그때 이 사람이 본인확인을 통과했다"는 사실은 고쳐 쓸 수 있으면 증거가 아니다.
 * 그래서 이 클래스에도 신원을 바꾸는 메서드가 없다.
 *
 * <p>열려 있는 것은 {@link #userId} 와 {@link #consumedAt} 둘뿐이고, 그것도 <b>한 번만</b>
 * 채울 수 있다.
 */
@Entity
@Getter
@Table(name = "identity_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider", length = 20, nullable = false)
    private String provider;

    /** 공급자 거래번호. 분쟁 시 공급자 기록과 대조하는 유일한 열쇠다. */
    @Column(name = "provider_tx_id", length = 100, nullable = false)
    private String providerTxId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 20, nullable = false)
    private IdentityPurpose purpose;

    @Column(name = "di", length = 88, nullable = false)
    private String di;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10, nullable = false)
    private Gender gender;

    @Column(name = "foreigner", nullable = false)
    private boolean foreigner;

    @Column(name = "phone", length = 30, nullable = false)
    private String phone;

    @Column(name = "carrier", length = 20)
    private String carrier;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at", nullable = false)
    private OffsetDateTime verifiedAt;

    @Column(name = "ip", length = 45)
    private String ip;

    private IdentityVerification(String provider, String providerTxId, IdentityPurpose purpose,
                                 VerifiedIdentity identity, String carrier,
                                 OffsetDateTime expiresAt, String ip) {
        this.provider = provider;
        this.providerTxId = providerTxId;
        this.purpose = purpose;
        this.di = identity.di();
        this.name = identity.name();
        this.birthDate = identity.birthDate();
        this.gender = identity.gender();
        this.foreigner = identity.foreigner();
        this.phone = identity.phone();
        this.carrier = carrier;
        this.expiresAt = expiresAt;
        this.verifiedAt = OffsetDateTime.now();
        this.ip = ip;
    }

    public static IdentityVerification record(String provider, String providerTxId,
                                              IdentityPurpose purpose, VerifiedIdentity identity,
                                              String carrier, OffsetDateTime expiresAt, String ip) {
        return new IdentityVerification(provider, providerTxId, purpose, identity, carrier, expiresAt, ip);
    }

    /** 저장된 값으로 신원을 되돌린다. 가입 시점에 회원 정보를 채우는 데 쓴다. */
    public VerifiedIdentity toIdentity() {
        return new VerifiedIdentity(di, name, birthDate, gender, foreigner, phone);
    }

    /**
     * 아직 쓸 수 있는가.
     *
     * <p>만료됐거나 이미 소진된 인증은 쓸 수 없다. 소진 검사가 있는 이유 — 한 번 받은
     * 본인확인으로 계정을 여러 개 만들거나, 가입에 쓴 인증을 비밀번호 재설정에 다시
     * 쓰는 경로를 막는다.
     */
    public boolean isUsable(OffsetDateTime now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    /** 가입·재설정을 마치며 소진 표시. 트리거가 두 번째 호출을 거부한다. */
    public void consume(Long userId) {
        this.userId = userId;
        this.consumedAt = OffsetDateTime.now();
    }

    /** 가입 없이 소진하는 경우(비밀번호 재설정). */
    public void consume() {
        this.consumedAt = OffsetDateTime.now();
    }

    public void linkTo(Long userId) {
        this.userId = userId;
    }
}
