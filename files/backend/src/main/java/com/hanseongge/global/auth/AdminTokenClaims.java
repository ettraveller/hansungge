package com.hanseongge.global.auth;

import com.hanseongge.domain.admin.entity.AdminRole;

/**
 * 검증을 통과한 관리자 토큰의 내용.
 *
 * <p>{@code tokenVersion} 은 {@code admin_users.token_version} 과 대조된다.
 * 값이 다르면 로그아웃·비밀번호 변경·권한 변경 이후 발급된 토큰이 아니므로 거부한다.
 */
public record AdminTokenClaims(
        Long adminId,
        String loginId,
        AdminRole role,
        AdminTokenType type,
        int tokenVersion
) {
}
