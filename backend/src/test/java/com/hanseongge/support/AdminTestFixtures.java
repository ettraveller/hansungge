package com.hanseongge.support;

import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminUser;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 관리자 테스트 계정 생성 도우미.
 *
 * <p>loginId 에 {@link #PREFIX} 를 강제한다. 임베디드 PostgreSQL 인스턴스를 테스트
 * 클래스들이 공유하므로, 정리 대상을 접두사로 식별할 수 있어야 다른 테스트가 만든
 * 데이터(예: 부트스트랩 테스트의 최초 관리자)를 지우는 사고가 나지 않는다.
 */
public final class AdminTestFixtures {

    public static final String PREFIX = "test_";

    /** 테스트 계정 공통 비밀번호. 비밀번호 정책(영문+숫자+특수문자 10자 이상)을 만족한다. */
    public static final String PASSWORD = "Test!Passw0rd";

    private AdminTestFixtures() {
    }

    public static AdminUser create(AdminUserRepository repository, PasswordEncoder encoder,
                                   String shortName, AdminRole role) {
        return repository.save(AdminUser.create(
                PREFIX + shortName,
                encoder.encode(PASSWORD),
                "테스트 " + role.name(),
                role));
    }
}
