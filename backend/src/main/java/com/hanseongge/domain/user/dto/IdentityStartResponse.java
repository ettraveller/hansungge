package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 본인확인 창을 띄울 정보.
 *
 * @param startUrl    사용자를 보낼 주소. 공급자 창이 열린다
 * @param signUpToken 돌아올 때 함께 보낼 토큰. 어느 가입의 인증인지 잇는 열쇠다
 */
@Schema(name = "IdentityStartResponse", description = "본인확인 시작")
public record IdentityStartResponse(
        @Schema(description = "본인확인 창 주소")
        String startUrl,

        @Schema(description = "가입 진행 토큰. 결과와 함께 그대로 돌려보낸다")
        String signUpToken
) {}
