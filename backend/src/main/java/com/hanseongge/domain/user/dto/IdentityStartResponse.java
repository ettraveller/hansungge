package com.hanseongge.domain.user.dto;

import com.hanseongge.domain.user.identity.IdentityStartForm;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 본인확인 창을 여는 폼과, 돌아온 뒤 결과와 함께 낼 토큰.
 *
 * <p>화면은 {@link #form} 을 해석하지 않는다 — hidden 필드로 옮겨 담아 그대로 전송한다.
 * 이니시스는 인증창을 폼 POST 로 연다.
 *
 * @param form  인증창을 여는 폼
 * @param token 이번 인증창의 거래번호가 담긴 새 진행 토큰. 결과와 함께 그대로 돌려보낸다
 */
@Schema(name = "IdentityStartResponse", description = "본인확인 시작")
public record IdentityStartResponse(
        @Schema(description = "인증창을 여는 폼")
        Form form,

        @Schema(description = "진행 토큰. 결과 제출 때 그대로 보낸다")
        String token
) {

    @Schema(name = "IdentityStartForm")
    public record Form(
            @Schema(description = "전송할 주소") String action,
            @Schema(description = "POST 또는 GET", example = "POST") String method,
            @Schema(description = "hidden 필드로 담아 보낼 값") Map<String, String> fields
    ) {
        public static Form from(IdentityStartForm form) {
            return new Form(form.action(), form.method(), form.fields());
        }
    }
}
