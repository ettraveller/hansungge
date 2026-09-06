package com.hanseongge.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 내 정보 수정.
 *
 * <h2>연락처가 여기 없다</h2>
 * 누락이 아니다. 휴대폰 번호는 <b>본인확인으로 확정된 값</b>이라 회원이 고칠 수 없다.
 * 고칠 수 있으면 "확인된 번호"가 아니라 "한 번 확인했다가 지금은 모르는 번호"가 되고,
 * 주문 안내와 매입 연락이 그 값 위에 선다.
 *
 * <p>받아 두고 조용히 무시하지 않는 이유 — 화면은 저장에 성공했다고 하는데 값이
 * 그대로면, 사용자는 자기가 뭘 잘못했는지 찾다가 고객센터에 전화한다. 필드를 없애면
 * 그런 화면을 만들 수 없다.
 *
 * <p>번호를 바꾸려면 본인확인을 다시 받아야 한다 (별도 흐름, 아직 없음).
 */
@Schema(name = "ProfileUpdateRequest", description = "내 정보 수정")
public record ProfileUpdateRequest(

        @Schema(description = "이름", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "이름을 입력해 주세요.")
        @Size(max = 100)
        String name
) {}
