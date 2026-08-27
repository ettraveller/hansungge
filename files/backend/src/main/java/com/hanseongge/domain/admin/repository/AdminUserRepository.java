package com.hanseongge.domain.admin.repository;

import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminStatus;
import com.hanseongge.domain.admin.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByLoginId(String loginId);

    boolean existsByLoginId(String loginId);

    /**
     * 활성 SUPER_ADMIN 수. 마지막 한 명을 강등·정지시켜 아무도 관리자 계정을 만들 수 없는
     * 상태가 되는 것을 막는 데 쓴다. 그 상태는 DB 를 직접 고치지 않으면 복구되지 않는다.
     */
    long countByRoleAndStatus(AdminRole role, AdminStatus status);
}
