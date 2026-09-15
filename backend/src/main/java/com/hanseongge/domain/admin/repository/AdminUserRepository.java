package com.hanseongge.domain.admin.repository;

import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminStatus;
import com.hanseongge.domain.admin.entity.AdminUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    /**
     * 활성 SUPER_ADMIN 수. 마지막 한 명을 강등·정지시켜 아무도 관리자 계정을 만들 수 없는
     * 상태가 되는 것을 막는 데 쓴다. 그 상태는 DB 를 직접 고치지 않으면 복구되지 않는다.
     */
    long countByRoleAndStatus(AdminRole role, AdminStatus status);

    /**
     * OTP 확인용 — 행을 잠그고 읽는다. 같은 6자리를 동시에 두 번 보내면 둘 다 "아직 안 쓴 구간"으로
     * 보고 통과할 수 있다. 잠가 두면 두 번째 요청은 첫 번째가 남긴 구간을 보고 거절된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM AdminUser a WHERE a.id = :id")
    Optional<AdminUser> findForUpdate(@Param("id") Long id);
}
