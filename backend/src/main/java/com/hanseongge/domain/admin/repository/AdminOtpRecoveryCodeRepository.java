package com.hanseongge.domain.admin.repository;

import com.hanseongge.domain.admin.entity.AdminOtpRecoveryCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminOtpRecoveryCodeRepository extends JpaRepository<AdminOtpRecoveryCode, Long> {

    Optional<AdminOtpRecoveryCode> findByAdminIdAndCodeDigestAndUsedAtIsNull(Long adminId, String codeDigest);

    long countByAdminIdAndUsedAtIsNull(Long adminId);

    /** 재발급·초기화. 옛 코드가 하나라도 남으면 새로 받은 의미가 없다. */
    @Modifying
    @Query("DELETE FROM AdminOtpRecoveryCode c WHERE c.adminId = :adminId")
    int deleteAllByAdminId(@Param("adminId") Long adminId);
}
