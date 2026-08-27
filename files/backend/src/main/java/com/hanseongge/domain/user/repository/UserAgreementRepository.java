package com.hanseongge.domain.user.repository;

import com.hanseongge.domain.user.entity.AgreementType;
import com.hanseongge.domain.user.entity.UserAgreement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserAgreementRepository extends JpaRepository<UserAgreement, Long> {

    List<UserAgreement> findByUserIdOrderByAgreedAtDescIdDesc(Long userId);

    /**
     * 약관별 <b>가장 최근</b> 동의 상태.
     *
     * <p>append-only 라 한 약관에 여러 행이 쌓인다. "지금 동의 상태"는 마지막 행이며,
     * 그것을 찾는 일을 화면마다 다시 구현하면 어딘가는 첫 행을 보고 틀린 답을 낸다.
     */
    @Query("""
            SELECT a FROM UserAgreement a
            WHERE a.userId = :userId
              AND a.id = (SELECT MAX(x.id) FROM UserAgreement x
                          WHERE x.userId = :userId AND x.agreementType = a.agreementType)
            ORDER BY a.agreementType
            """)
    List<UserAgreement> findLatestPerType(@Param("userId") Long userId);

    @Query("""
            SELECT a.agreed FROM UserAgreement a
            WHERE a.userId = :userId AND a.agreementType = :type
            ORDER BY a.id DESC
            LIMIT 1
            """)
    Boolean findLatestAgreed(@Param("userId") Long userId, @Param("type") AgreementType type);
}
