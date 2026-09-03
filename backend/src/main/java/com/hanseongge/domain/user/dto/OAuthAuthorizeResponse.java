package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 소셜 로그인 시작 정보.
 *
 * <p>{@code state} 를 프론트엔드에도 돌려준다. 브라우저가 콜백으로 돌아왔을 때
 * 자기가 저장해 둔 값과 대조하기 위해서다 — 서버 서명 검증과 <b>독립적인</b> 두 번째
 * 방어선이고, 인가코드 주입을 막는 실질적인 장치다.
 */
@Schema(name = "OAuthAuthorizeResponse", description = "소셜 로그인 시작")
public record OAuthAuthorizeResponse(

        @Schema(description = "이 주소로 이동시킨다")
        String authorizeUrl,

        @Schema(description = "sessionStorage 에 보관했다가 콜백에서 대조한다")
        String state
) {}
