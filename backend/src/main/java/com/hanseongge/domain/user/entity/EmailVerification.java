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
 * 이메일 인증번호 ({@code email_verifications}).
 *
 * <h2>원문을 저장하지 않는다</h2>
 * {@code RefreshToken} 과 같은 방식(SHA-256)이고 같은 이유다 — DB 를 읽을 수 있는 사람이
 * 남의 가입을 가로챌 수 있으면 안 된다.
 *
 * <p>BCrypt 를 쓰지 않는 것도 같은 이유다. 조회 키여야 하고, 만료 5분짜리 6자리 숫자에
 * 매번 BCrypt 비용을 치를 이유가 없다.
 *
 * <h2>시도 횟수를 센다</h2>
 * 6자리 숫자는 100만 분의 1이지만, 시도를 막지 않으면 만료 전에 다 훑을 수 있다.
 */
@Entity
@Getter
@Table(name = "email_verifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(name = "code_hash", length = 128, nullable = false)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 20, nullable = false)
    private EmailVerificationPurpose purpose;

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

    private EmailVerification(String email, String rawCode, EmailVerificationPurpose purpose,
                              OffsetDateTime expiresAt, String ip) {
        // 소문자로 저장한다. 조회도 소문자로 해야 대소문자만 다른 주소로 우회할 수 없다.
        this.email = email.toLowerCase();
        this.codeHash = hash(rawCode);
        this.purpose = purpose;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
        this.ip = ip;
    }

    public static EmailVerification issue(String email, String rawCode,
                                          EmailVerificationPurpose purpose,
                                          OffsetDateTime expiresAt, String ip) {
        return new EmailVerification(email, rawCode, purpose, expiresAt, ip);
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

    /** 인증을 마쳤고 아직 가입에 쓰지 않은 상태인가. */
    public boolean isVerifiedAndUnused(OffsetDateTime now) {
        return verifiedAt != null && consumedAt == null && expiresAt.isAfter(now);
    }

    public void consume() {
        this.consumedAt = OffsetDateTime.now();
    }
}
