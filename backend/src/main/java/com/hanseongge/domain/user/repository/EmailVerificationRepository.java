package com.hanseongge.domain.user.repository;

import com.hanseongge.domain.user.entity.EmailVerification;
import com.hanseongge.domain.user.entity.EmailVerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface EmailVerificationRepository extends JpaRepository<EmailVerification, Long> {

    /**
     * 그 주소로 마지막에 보낸 인증번호.
     *
     * <p>여러 번 요청하면 행이 여러 개 쌓인다. 지우지 않는 이유는 "언제 몇 번 요청했는가"가
     * 남아야 이상 사용을 판단할 수 있기 때문이고, 대조는 <b>가장 최근 것</b>으로만 한다 —
     * 이전 번호가 계속 통하면 만료의 의미가 없다.
     */
    @Query("""
            SELECT e FROM EmailVerification e
            WHERE lower(e.email) = lower(:email) AND e.purpose = :purpose
            ORDER BY e.createdAt DESC
            LIMIT 1
            """)
    Optional<EmailVerification> findLatest(@Param("email") String email,
                                           @Param("purpose") EmailVerificationPurpose purpose);

    /** 재발송 쿨다운 판정용 — 최근에 보낸 것이 있는지. */
    @Query("""
            SELECT count(e) > 0 FROM EmailVerification e
            WHERE lower(e.email) = lower(:email) AND e.purpose = :purpose
              AND e.createdAt > :since
            """)
    boolean existsRecent(@Param("email") String email,
                         @Param("purpose") EmailVerificationPurpose purpose,
                         @Param("since") OffsetDateTime since);
}
