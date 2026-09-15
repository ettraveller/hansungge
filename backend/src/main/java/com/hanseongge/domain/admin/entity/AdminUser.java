package com.hanseongge.domain.admin.entity;

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
 * 관리자 계정 ({@code admin_users}).
 *
 * <p>일반 사용자({@code users})와 완전히 분리된 테이블이다. 사용자는 OAuth2 만 쓰므로
 * 비밀번호를 보관하지 않고, 비밀번호를 가진 주체는 이 엔티티뿐이다.
 *
 * <p>{@code password_hash} 는 BCrypt(cost 12) 결과만 담는다. 평문이 이 필드에
 * 들어가는 경로는 존재하지 않는다 — 생성자가 이미 해시된 값만 받는다.
 *
 * <p><b>상태 전이는 전부 이 클래스의 메서드로만 일어난다.</b> 서비스가 setter 로
 * status 나 실패 횟수를 직접 만지면 "5회 실패 = 잠금" 규칙이 호출부마다 흩어진다.
 */
@Entity
@Getter
@Table(name = "admin_users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", length = 60, nullable = false, unique = true)
    private String loginId;

    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private AdminRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AdminStatus status;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    /** 연속 실패 횟수. 성공 시 0 으로 되돌린다 (V6). */
    @Column(name = "login_fail_count", nullable = false)
    private int loginFailCount;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "password_changed_at")
    private OffsetDateTime passwordChangedAt;

    /** 발급된 JWT 의 {@code tv} 클레임과 대조되는 토큰 세대 (V6). */
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    /** OTP 키 암호문 (V20). 등록 중에도 값이 있다 — 켜졌는지는 {@link #otpEnabledAt} 이 말한다. */
    @Column(name = "otp_secret", length = 200)
    private String otpSecret;

    @Column(name = "otp_enabled_at")
    private OffsetDateTime otpEnabledAt;

    /** 마지막으로 통과한 30초 구간. 같은 6자리 재사용을 막는다. */
    @Column(name = "otp_last_step")
    private Long otpLastStep;

    /** OTP 연속 실패. 비밀번호 실패와 따로 센다. */
    @Column(name = "otp_fail_count", nullable = false)
    private int otpFailCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private AdminUser(String loginId, String passwordHash, String name, AdminRole role) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.status = AdminStatus.ACTIVE;
        this.loginFailCount = 0;
        this.tokenVersion = 0;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 관리자 생성.
     *
     * @param passwordHash BCrypt 해시. <b>평문을 넘기면 안 된다.</b>
     */
    public static AdminUser create(String loginId, String passwordHash, String name, AdminRole role) {
        return new AdminUser(loginId, passwordHash, name, role);
    }

    // ---------- 로그인 ----------

    /** 로그인 성공(OTP 까지 통과). 실패 카운터를 모두 리셋한다. */
    public void recordLogin() {
        this.lastLoginAt = OffsetDateTime.now();
        this.loginFailCount = 0;
        this.otpFailCount = 0;
        touch();
    }

    /**
     * 비밀번호만 통과. 비밀번호 실패 횟수만 되돌린다 — OTP 실패 횟수를 여기서 되돌리면
     * 비밀번호를 아는 사람이 로그인을 반복하며 6자리를 무한히 찍어 볼 수 있다.
     */
    public void recordPasswordVerified() {
        this.loginFailCount = 0;
        touch();
    }

    /**
     * 로그인 실패 1회 기록. 임계치에 도달하면 스스로 잠긴다.
     *
     * @return 이번 실패로 잠금 상태가 되었으면 true
     */
    public boolean recordLoginFailure(int maxFailures) {
        this.loginFailCount++;
        touch();

        if (this.loginFailCount >= maxFailures && this.status == AdminStatus.ACTIVE) {
            this.status = AdminStatus.LOCKED;
            this.lockedAt = OffsetDateTime.now();
            // 잠긴 계정으로 발급된 토큰이 살아 있으면 잠금이 무의미하다.
            this.tokenVersion++;
            return true;
        }
        return false;
    }

    // ---------- OTP ----------

    public boolean isOtpEnabled() {
        return this.otpEnabledAt != null;
    }

    /** 등록 시작 — 새 키를 둔다. 아직 켜지지 않았다. */
    public void prepareOtp(String encryptedSecret) {
        this.otpSecret = encryptedSecret;
        this.otpEnabledAt = null;
        this.otpLastStep = null;
        touch();
    }

    /**
     * 통과한 30초 구간을 기록한다.
     *
     * @return 이미 쓴 구간(또는 그 이전)이면 false — 같은 6자리를 다시 넣은 것이다
     */
    public boolean acceptOtpStep(long step) {
        if (this.otpLastStep != null && step <= this.otpLastStep) {
            return false;
        }
        this.otpLastStep = step;
        touch();
        return true;
    }

    /** 등록 완료. 키는 {@link #prepareOtp} 에서 이미 들어와 있어야 한다(DB 제약도 같다). */
    public void enableOtp() {
        this.otpEnabledAt = OffsetDateTime.now();
        this.otpFailCount = 0;
        touch();
    }

    public void recordRecoveryCodeUse() {
        touch();
    }

    /**
     * OTP 실패 1회. 임계치에서 스스로 잠긴다.
     *
     * @return 이번 실패로 잠겼으면 true
     */
    public boolean recordOtpFailure(int maxFailures) {
        this.otpFailCount++;
        touch();

        if (this.otpFailCount >= maxFailures && this.status == AdminStatus.ACTIVE) {
            this.status = AdminStatus.LOCKED;
            this.lockedAt = OffsetDateTime.now();
            this.tokenVersion++;
            return true;
        }
        return false;
    }

    /**
     * OTP 초기화 — 다음 로그인에서 다시 등록한다. 기존 토큰도 끊는다: 잃어버린 휴대폰에
     * 열려 있던 관리자 화면까지 막아야 한다.
     */
    public void resetOtp() {
        this.otpSecret = null;
        this.otpEnabledAt = null;
        this.otpLastStep = null;
        this.otpFailCount = 0;
        this.tokenVersion++;
        touch();
    }

    // ---------- 계정 관리 ----------

    /** 비밀번호 교체. 기존에 발급된 모든 토큰을 무효화한다. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = OffsetDateTime.now();
        this.tokenVersion++;
        touch();
    }

    /**
     * 상태 변경. ACTIVE 로 되돌릴 때는 실패 카운터도 함께 푼다 —
     * 잠금 해제 직후 1회 실패로 다시 잠기면 해제한 의미가 없다.
     */
    public void changeStatus(AdminStatus newStatus) {
        this.status = newStatus;
        if (newStatus == AdminStatus.ACTIVE) {
            this.loginFailCount = 0;
            this.otpFailCount = 0;
            this.lockedAt = null;
        } else {
            if (newStatus == AdminStatus.LOCKED) {
                this.lockedAt = OffsetDateTime.now();
            }
            // 비활성/잠금 계정의 기존 토큰을 즉시 끊는다.
            this.tokenVersion++;
        }
        touch();
    }

    public void changeRole(AdminRole newRole) {
        this.role = newRole;
        // 권한이 담긴 토큰이 이미 나가 있다. 새 권한으로 다시 받게 한다.
        this.tokenVersion++;
        touch();
    }

    public void changeName(String newName) {
        this.name = newName;
        touch();
    }

    /** 로그아웃 — 무상태 JWT 를 실제로 끊는 유일한 수단이다. */
    public void invalidateTokens() {
        this.tokenVersion++;
        touch();
    }

    public boolean isActive() {
        return this.status == AdminStatus.ACTIVE;
    }

    public boolean isLocked() {
        return this.status == AdminStatus.LOCKED;
    }

    private void touch() {
        this.updatedAt = OffsetDateTime.now();
    }
}
