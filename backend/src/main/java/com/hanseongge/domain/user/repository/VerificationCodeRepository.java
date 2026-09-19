package com.hanseongge.domain.user.repository;

import com.hanseongge.domain.user.entity.VerificationChannel;
import com.hanseongge.domain.user.entity.VerificationCode;
import com.hanseongge.domain.user.entity.VerificationPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 인증번호. 대상({@code target})은 호출 전에 정규화돼 있어야 한다 — 이메일은 소문자,
 * 휴대폰은 숫자만. 여기서 {@code lower()} 를 씌우지 않는 이유이고, 그래야 V22 인덱스를 탄다.
 */
public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {

    /**
     * 그 대상으로 마지막에 보낸 인증번호.
     *
     * <p>여러 번 요청하면 행이 여러 개 쌓인다. 지우지 않는 이유는 "언제 몇 번 요청했는가"가
     * 남아야 이상 사용을 판단할 수 있기 때문이고, 대조는 <b>가장 최근 것</b>으로만 한다 —
     * 이전 번호가 계속 통하면 만료의 의미가 없다.
     */
    @Query("""
            SELECT v FROM VerificationCode v
            WHERE v.target = :target AND v.channel = :channel AND v.purpose = :purpose
            ORDER BY v.createdAt DESC
            LIMIT 1
            """)
    Optional<VerificationCode> findLatest(@Param("target") String target,
                                          @Param("channel") VerificationChannel channel,
                                          @Param("purpose") VerificationPurpose purpose);

    /** 기간 안에 보낸 건수 — 재발송 쿨다운(1건이라도 있으면)과 시간당 한도에 쓴다. */
    @Query("""
            SELECT count(v) FROM VerificationCode v
            WHERE v.target = :target AND v.channel = :channel AND v.purpose = :purpose
              AND v.createdAt > :since
            """)
    long countSince(@Param("target") String target,
                    @Param("channel") VerificationChannel channel,
                    @Param("purpose") VerificationPurpose purpose,
                    @Param("since") OffsetDateTime since);
}
