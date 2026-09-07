package com.hanseongge.domain.user.dto;

import com.hanseongge.domain.user.entity.Gender;
import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 회원 상세 (관리자).
 *
 * <h2>여기서만 가리지 않는다</h2>
 * 목록과 달리 이메일·이름·휴대폰이 <b>원본 그대로</b> 나간다. 문의에 답하거나 주문을
 * 확인하려면 실제 값이 필요하고, 그것이 이 화면의 목적이다.
 *
 * <p>대신 이 응답을 만드는 조회는 <b>감사로그에 남는다.</b> 개인정보를 열람할 수 있게
 * 하는 것과 아무도 모르게 열람하도록 두는 것은 다른 문제다.
 *
 * <h2>{@code di} 는 담지 않는다</h2>
 * 본인확인기관이 발급한 중복가입확인정보다. 관리자가 그 값을 보고 할 수 있는 일이 없고,
 * 새어 나가면 되돌릴 방법도 없다. "본인확인을 마쳤는가"만 있으면 충분하다.
 *
 * <p>{@code passwordHash} 도 마찬가지다 — BCrypt 해시라 되돌릴 수는 없지만, 굳이
 * 내보낼 이유가 없는 값은 내보내지 않는다.
 */
@Schema(name = "AdminMemberDetailResponse", description = "회원 상세 (관리자)")
public record AdminMemberDetailResponse(

        @Schema(description = "회원 ID", example = "7")
        Long id,

        @Schema(description = "이메일 (원본)", example = "hong@example.com")
        String email,

        @Schema(description = "이름 (원본)", example = "홍길동")
        String name,

        @Schema(description = "휴대폰 (원본)", example = "010-1234-5678")
        String phone,

        @Schema(description = "회원 상태", example = "ACTIVE")
        UserStatus status,

        @Schema(description = "상태 한글 표기", example = "정상")
        String statusLabel,

        @Schema(description = "생년월일. 본인확인 응답으로만 채워진다")
        LocalDate birthDate,

        @Schema(description = "성별. 본인확인 응답으로만 채워진다", example = "MALE")
        Gender gender,

        @Schema(description = "외국인 여부", example = "false")
        Boolean foreigner,

        @Schema(description = "비밀번호 로그인 가능 여부", example = "true")
        boolean hasPassword,

        @Schema(description = "휴대폰 본인확인 완료 여부", example = "true")
        boolean identityVerified,

        @Schema(description = "이메일 인증 시각")
        OffsetDateTime emailVerifiedAt,

        @Schema(description = "본인확인 시각")
        OffsetDateTime phoneVerifiedAt,

        @Schema(description = "비밀번호 마지막 변경 시각")
        OffsetDateTime passwordChangedAt,

        @Schema(description = "연속 로그인 실패 횟수", example = "0")
        int loginFailCount,

        @Schema(description = "잠긴 시각. 30분이 지나면 스스로 풀린다")
        OffsetDateTime lockedAt,

        @Schema(description = "지금 잠겨 있는가", example = "false")
        boolean locked,

        @Schema(description = "연결된 소셜 계정", example = "[\"GOOGLE\"]")
        List<OAuthProviderType> socialProviders,

        @Schema(description = "가입 시각")
        OffsetDateTime createdAt,

        @Schema(description = "마지막 변경 시각")
        OffsetDateTime updatedAt
) {

    public static AdminMemberDetailResponse of(User user, List<OAuthProviderType> providers,
                                               OffsetDateTime now) {
        return new AdminMemberDetailResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getStatus(),
                user.getStatus().displayName(),
                user.getBirthDate(),
                user.getGender(),
                user.getForeigner(),
                user.hasPassword(),
                user.isIdentityVerified(),
                user.getEmailVerifiedAt(),
                user.getPhoneVerifiedAt(),
                user.getPasswordChangedAt(),
                user.getLoginFailCount(),
                user.getLockedAt(),
                user.isLocked(now),
                providers,
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
