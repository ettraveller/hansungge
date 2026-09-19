package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 비밀번호 찾기 — 본인확인을 통과했다. 5분 안에 새 비밀번호를 정한다.
 *
 * <p>계정 선택 화면이 없다. 같은 DI 로 활성 계정은 하나뿐이다 ({@code ux_users_di_active}).
 */
@Schema(name = "PasswordResetGrantResponse", description = "비밀번호 재설정 권한")
public record PasswordResetGrantResponse(
        @Schema(description = "재설정 토큰. 5분 동안 한 번 쓸 수 있다")
        String resetToken,

        @Schema(description = "찾은 계정의 이메일(가림)")
        String accountEmail,

        @Schema(description = "찾은 계정의 휴대폰(가림)")
        String accountPhone
) {}
