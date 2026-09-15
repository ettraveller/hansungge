package com.hanseongge.domain.admin.entity;

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
 * 관리자 OTP 복구 코드 ({@code admin_otp_recovery_codes}).
 *
 * <p>휴대폰을 잃어버렸을 때 인증 앱 대신 한 번 쓰는 코드다. 평문은 등록·재발급 순간에만
 * 화면에 나가고, 여기에는 요약값만 남는다.
 */
@Entity
@Getter
@Table(name = "admin_otp_recovery_codes")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminOtpRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "code_digest", length = 64, nullable = false)
    private String codeDigest;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private AdminOtpRecoveryCode(Long adminId, String codeDigest) {
        this.adminId = adminId;
        this.codeDigest = codeDigest;
        this.createdAt = OffsetDateTime.now();
    }

    public static AdminOtpRecoveryCode issue(Long adminId, String codeDigest) {
        return new AdminOtpRecoveryCode(adminId, codeDigest);
    }

    /** 한 번 쓰면 끝이다. 지우지 않고 표시만 한다 — 언제 복구 코드로 들어왔는지가 남는다. */
    public void markUsed() {
        this.usedAt = OffsetDateTime.now();
    }
}
