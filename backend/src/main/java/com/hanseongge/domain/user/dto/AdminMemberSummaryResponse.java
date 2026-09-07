package com.hanseongge.domain.user.dto;

import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.entity.UserStatus;
import com.hanseongge.global.util.PrivacyMask;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 회원 목록 한 줄 (관리자).
 *
 * <h2>목록에서는 가린다</h2>
 * 이메일·이름·휴대폰이 <b>가려진 채로</b> 나간다. 목록은 한 번에 20줄이고 검색으로
 * 훑을 수 있어서, 여기서 전부 드러내면 회원 데이터베이스를 페이지 단위로 내려받는
 * 창구가 된다. 전체 값이 필요하면 상세로 들어가고 <b>그 조회는 감사로그에 남는다.</b>
 *
 * <p>가리는 일은 이 DTO 가 만들어질 때 끝난다 — 원본 필드가 아예 없어서 실수로 담을
 * 수도 없다.
 *
 * <p>{@code di}(중복가입확인정보)는 어떤 응답에도 담지 않는다. 관리자가 볼 이유가 없고,
 * 새어 나가면 되돌릴 수 없는 값이다.
 */
@Schema(name = "AdminMemberSummaryResponse", description = "회원 목록 항목 (관리자)")
public record AdminMemberSummaryResponse(

        @Schema(description = "회원 ID", example = "7")
        Long id,

        @Schema(description = "가려진 이메일", example = "ho********@example.com")
        String email,

        @Schema(description = "가려진 이름", example = "홍**")
        String name,

        @Schema(description = "가려진 휴대폰", example = "010-****-5678")
        String phone,

        @Schema(description = "회원 상태", example = "ACTIVE")
        UserStatus status,

        @Schema(description = "상태 한글 표기", example = "정상")
        String statusLabel,

        @Schema(description = "비밀번호 로그인이 가능한가. 소셜로만 가입했으면 false",
                example = "true")
        boolean hasPassword,

        @Schema(description = "휴대폰 본인확인을 마쳤는가", example = "true")
        boolean identityVerified,

        @Schema(description = "로그인 실패로 잠겨 있는가. 30분이 지나면 스스로 풀린다",
                example = "false")
        boolean locked,

        @Schema(description = "연속 로그인 실패 횟수", example = "0")
        int loginFailCount,

        @Schema(description = "가입 시각")
        OffsetDateTime createdAt
) {

    public static AdminMemberSummaryResponse of(User user, OffsetDateTime now) {
        return new AdminMemberSummaryResponse(
                user.getId(),
                PrivacyMask.email(user.getEmail()),
                PrivacyMask.name(user.getName()),
                PrivacyMask.phone(user.getPhone()),
                user.getStatus(),
                user.getStatus().displayName(),
                user.hasPassword(),
                user.isIdentityVerified(),
                user.isLocked(now),
                user.getLoginFailCount(),
                user.getCreatedAt());
    }
}
