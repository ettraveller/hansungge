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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;

/**
 * 인증번호 — 메일·문자 공용 ({@code verification_codes}).
 *
 * <h2>원문을 저장하지 않는다</h2>
 * {@code RefreshToken} 과 같은 방식(SHA-256)이고 같은 이유다 — DB 를 읽을 수 있는 사람이
 * 남의 로그인을 가로챌 수 있으면 안 된다.
 *
 * <p>BCrypt 를 쓰지 않는 것도 같은 이유다. 조회 키여야 하고, 만료 5분짜리 6자리 숫자에
 * 매번 BCrypt 비용을 치를 이유가 없다.
 *
 * <h2>시도 횟수를 센다</h2>
 * 6자리 숫자는 100만 분의 1이지만, 시도를 막지 않으면 만료 전에 다 훑을 수 있다.
 *
 * <h2>대상은 정규화해서 받는다</h2>
 * 이메일은 소문자, 휴대폰은 숫자만. 모양이 다른 같은 대상으로 쿨다운·시간당 한도를
 * 우회할 수 없게 하기 위해서다. 정규화는 서비스가 하고 여기서는 그대로 저장한다.
 */
@Entity
@Getter
@Table(name = "verification_codes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target", length = 255, nullable = false)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 10, nullable = false)
    private VerificationChannel channel;

    @Column(name = "code_hash", length = 128, nullable = false)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 20, nullable = false)
    private VerificationPurpose purpose;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "consumed_at")
    private OffsetDateTime consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "ip", length = 45)
    private String ip;

    private VerificationCode(VerificationChannel channel, String target, String rawCode,
                             VerificationPurpose purpose, OffsetDateTime expiresAt, String ip) {
        this.channel = channel;
        this.target = target;
        this.codeHash = hash(rawCode);
        this.purpose = purpose;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
        this.ip = ip;
    }

    /** @param target 정규화된 대상 — 이메일은 소문자, 휴대폰은 숫자만 */
    public static VerificationCode issue(VerificationChannel channel, String target, String rawCode,
                                         VerificationPurpose purpose, OffsetDateTime expiresAt,
                                         String ip) {
        return new VerificationCode(channel, target, rawCode, purpose, expiresAt, ip);
    }

    public static String hash(String rawCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawCode.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 쓸 수 없습니다.", e);
        }
    }

    /**
     * 코드를 대조한다.
     *
     * <p>맞든 틀리든 <b>시도 횟수는 올라간다.</b> 맞았을 때만 세지 않으면 틀린 시도를
     * 무제한 할 수 있어 세는 의미가 없다.
     *
     * <p>⚠️ 이 메서드를 부른 트랜잭션에서 예외를 던지면 증가한 횟수가 롤백된다.
     * 호출자는 결과를 값으로 받아 트랜잭션 밖에서 예외로 바꿔야 한다.
     */
    public boolean matches(String rawCode) {
        this.attemptCount += 1;
        boolean ok = MessageDigest.isEqual(
                codeHash.getBytes(StandardCharsets.UTF_8),
                hash(rawCode).getBytes(StandardCharsets.UTF_8));
        if (ok) {
            this.verifiedAt = OffsetDateTime.now();
        }
        return ok;
    }

    public boolean isExpired(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean hasAttemptsLeft(int max) {
        return attemptCount < max;
    }

    /** 인증을 마쳤고 아직 쓰지 않은 상태인가. */
    public boolean isVerifiedAndUnused(OffsetDateTime now) {
        return verifiedAt != null && consumedAt == null && expiresAt.isAfter(now);
    }

    /** 성공 즉시 소진 — 한 번 통과한 번호가 다시 통하면 가로챈 사람도 쓸 수 있다. */
    public void consume() {
        this.consumedAt = OffsetDateTime.now();
    }
}
