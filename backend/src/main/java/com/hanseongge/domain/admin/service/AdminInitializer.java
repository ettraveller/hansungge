package com.hanseongge.domain.admin.service;

import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminUser;
import com.hanseongge.domain.admin.repository.AdminUserRepository;
import com.hanseongge.global.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 최초 관리자 계정 부트스트랩 (STEP 3 요구사항 8).
 *
 * <p>동작 규칙:
 * <ol>
 *   <li>{@code admin_users} 에 계정이 하나라도 있으면 <b>아무것도 하지 않는다.</b>
 *       이미 운영 중인 시스템에 계정이 끼어드는 사고를 막는다.</li>
 *   <li>비어 있고 {@code ADMIN_INIT_ID}/{@code ADMIN_INIT_PASSWORD} 가 주입되어 있으면
 *       SUPER_ADMIN 1개를 BCrypt 해시로 생성한다.</li>
 *   <li>환경변수가 없으면 생성을 건너뛰고 WARN 을 남긴다.</li>
 * </ol>
 *
 * <p><b>기본 계정을 코드에 박아두지 않는 이유:</b> 마이그레이션이나 소스에 고정 계정이
 * 들어가면 저장소를 읽을 수 있는 누구나 운영 관리자 자격을 얻는다. V1 마이그레이션이
 * 시드 INSERT 를 두지 않은 것도 같은 이유다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long existing = adminUserRepository.count();
        if (existing > 0) {
            log.info("[ADMIN-INIT] 관리자 계정이 이미 {}개 존재합니다. 초기 생성을 건너뜁니다.", existing);
            return;
        }

        AppProperties.AdminInit init = appProperties.adminInit();
        if (!init.isConfigured()) {
            log.warn("""
                    [ADMIN-INIT] 관리자 계정이 없고 ADMIN_INIT_ID / ADMIN_INIT_PASSWORD 도 주입되지 않았습니다.
                    초기 관리자 생성을 건너뜁니다. 관리자 로그인이 필요하면 두 환경변수를 설정 후 재기동하세요.
                      PowerShell : $env:ADMIN_INIT_ID="admin"; $env:ADMIN_INIT_PASSWORD="<강한 비밀번호>"
                      bash       : export ADMIN_INIT_ID=admin ADMIN_INIT_PASSWORD='<강한 비밀번호>'""");
            return;
        }

        if (adminUserRepository.existsByLoginId(init.id())) {
            log.info("[ADMIN-INIT] loginId '{}' 가 이미 존재합니다. 생성을 건너뜁니다.", init.id());
            return;
        }

        AdminUser admin = AdminUser.create(
                init.id(),
                passwordEncoder.encode(init.password()),
                "최초 관리자",
                AdminRole.SUPER_ADMIN);
        adminUserRepository.save(admin);

        // 비밀번호는 어떤 형태로도 로그에 남기지 않는다.
        log.info("[ADMIN-INIT] SUPER_ADMIN 계정을 생성했습니다. loginId={}", init.id());
        log.warn("[ADMIN-INIT] 최초 로그인 후 비밀번호를 변경하고, ADMIN_INIT_PASSWORD 환경변수를 제거하세요.");
    }
}
