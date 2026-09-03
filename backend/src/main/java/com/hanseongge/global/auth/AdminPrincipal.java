package com.hanseongge.global.auth;

import com.hanseongge.domain.admin.entity.AdminRole;
import com.hanseongge.domain.admin.entity.AdminUser;

/**
 * 인증된 관리자. {@code SecurityContext} 의 principal 로 들어간다.
 *
 * <p>엔티티({@link AdminUser})를 그대로 담지 않는다. 영속성 컨텍스트 밖의 엔티티가
 * 요청 전 구간을 떠다니면 지연로딩 예외와 의도치 않은 dirty checking 의 출처가 된다.
 * 필요한 값만 복사한 불변 객체로 들고 다닌다.
 */
public record AdminPrincipal(
        Long id,
        String loginId,
        String name,
        AdminRole role
) {

    public static AdminPrincipal from(AdminUser admin) {
        return new AdminPrincipal(admin.getId(), admin.getLoginId(), admin.getName(), admin.getRole());
    }

    /** Spring Security 권한 문자열. 계층 설정(AdminRole 상하관계)이 이 값을 기준으로 동작한다. */
    public String authority() {
        return "ROLE_" + role.name();
    }
}
