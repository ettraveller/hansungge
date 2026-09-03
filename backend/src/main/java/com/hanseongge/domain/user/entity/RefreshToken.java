package com.hanseongge.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Refresh 토큰 ({@code refresh_tokens}).
 *
 * <h2>원문을 저장하지 않는다</h2>
 * DB 에 남는 것은 SHA-256 해시뿐이다. 저장된 값이 그대로 인증에 쓰이면, DB 를 읽을 수 있는
 * 사람이 곧 모든 회원의 세션을 갖게 된다. 비밀번호를 해시로 보관하는 것과 같은 이유다.
 *
 * <p>BCrypt 가 아니라 SHA-256 인 것은 의도적이다. Refresh 토큰은 128비트 이상의 무작위
 * 서명 문자열이라 사전 공격 대상이 아니고, 매 재발급마다 BCrypt(cost 12)를 돌리면
 * 로그인 유지 요청 하나에 수백 밀리초가 붙는다. 해시로 <b>조회</b>해야 하는 것도 이유다.
 *
 * <h2>왜 관리자는 이 테이블을 쓰지 않는가</h2>
 * 관리자는 {@code admin_users.token_version} 으로 회수한다. 탈취 시 피해가 커서 로그아웃·
 * 비밀번호 변경 즉시 <b>이미 발급된 Access 토큰까지</b> 무효화해야 하기 때문이다.
 * 회원은 그 정도 즉시성이 필요 없고, 트래픽이 훨씬 많아 매 요청 대조가 부담이다.
 * 대신 Refresh 를 DB 로 관리해 "이 기기에서 로그아웃"이 가능하다.
 */
@Entity
@Getter
@Table(name = "refresh_tokens")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    /** {@code subject_type} CHECK 제약과 값이 일치해야 한다. */
    public static final String SUBJECT_USER = "USER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject_type", length = 10, nullable = false)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "token_hash", length = 128, nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private RefreshToken(String subjectType, Long subjectId, String tokenHash, OffsetDateTime expiresAt) {
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.revoked = false;
        this.createdAt = OffsetDateTime.now();
    }

    public static RefreshToken issueForUser(Long userId, String rawToken, OffsetDateTime expiresAt) {
        return new RefreshToken(SUBJECT_USER, userId, hash(rawToken), expiresAt);
    }

    /**
     * 조회·검증용 해시.
     *
     * <p>SHA-256 은 JDK 표준 구현이라 {@link NoSuchAlgorithmException} 이 실제로 발생할 수
     * 없다. 그래도 조용히 삼키지 않는다 — 만약 발생한다면 토큰을 검증할 수 없다는 뜻이고,
     * 그 상태로 계속 도는 것보다 기동/요청이 실패하는 편이 안전하다.
     */
    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다", e);
        }
    }

    /** 로그아웃·재발급 회전 시 호출. 행을 지우지 않아 "언제 끊겼는가"가 남는다. */
    public void revoke() {
        this.revoked = true;
    }

    public boolean isUsable(OffsetDateTime now) {
        return !revoked && expiresAt.isAfter(now);
    }
}
