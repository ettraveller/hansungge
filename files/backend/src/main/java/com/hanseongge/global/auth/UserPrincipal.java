package com.hanseongge.global.auth;

import com.hanseongge.domain.user.entity.User;

/**
 * 인증된 회원. {@code SecurityContext} 의 principal 로 들어간다.
 *
 * <p>엔티티({@link User})를 그대로 담지 않는다. 영속성 컨텍스트 밖의 엔티티가 요청 전
 * 구간을 떠다니면 지연로딩 예외와 의도치 않은 dirty checking 의 출처가 된다.
 */
public record UserPrincipal(
        Long id,
        String email,
        String name
) {

    /** 회원은 단일 등급이다. 관리자와 달리 권한 계층이 없다. */
    public static final String AUTHORITY = "ROLE_USER";

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getName());
    }
}
