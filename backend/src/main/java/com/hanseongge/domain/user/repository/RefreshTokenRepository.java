package com.hanseongge.domain.user.repository;

import com.hanseongge.domain.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** 원문이 아니라 해시로 찾는다. 원문은 어디에도 저장되어 있지 않다. */
    Optional<RefreshToken> findByTokenHashAndSubjectType(String tokenHash, String subjectType);

    /** "모든 기기에서 로그아웃". 탈퇴·정지 시에도 쓴다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE RefreshToken r SET r.revoked = true
            WHERE r.subjectType = :subjectType AND r.subjectId = :subjectId AND r.revoked = false
            """)
    int revokeAll(@Param("subjectType") String subjectType, @Param("subjectId") Long subjectId);

    /**
     * 만료된 행 정리 (STEP 18 배치용).
     * 지우는 것은 <b>만료된 것</b>뿐이다. revoked 행은 "언제 끊겼는가"의 기록이라 남긴다.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :before")
    int deleteExpired(@Param("before") OffsetDateTime before);
}
