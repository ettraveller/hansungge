package com.hanseongge.domain.user.dto;

import com.hanseongge.domain.user.entity.AgreementType;
import com.hanseongge.domain.user.entity.OAuthProviderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 로그인 화면이 그릴 것들.
 *
 * <p>사용 가능한 제공자를 서버가 알려준다. 프론트엔드에 버튼 목록을 하드코딩하면
 * 앱 검수가 끝나지 않은 제공자 버튼이 눌리고, 사용자는 실패 화면을 본다.
 */
@Schema(name = "AuthProvidersResponse", description = "로그인 화면 구성 정보")
public record AuthProvidersResponse(

        @Schema(description = "지금 사용할 수 있는 소셜 로그인")
        List<ProviderItem> providers,

        @Schema(description = "가입 시 표시할 약관 목록")
        List<AgreementItem> agreements
) {

    @Schema(name = "AuthProviderItem")
    public record ProviderItem(
            @Schema(description = "제공자 코드", example = "KAKAO") OAuthProviderType code,
            @Schema(description = "표시명", example = "카카오") String name
    ) {}

    @Schema(name = "AuthAgreementItem")
    public record AgreementItem(
            @Schema(description = "약관 코드", example = "TERMS") AgreementType code,
            @Schema(description = "표시 문구") String name,
            @Schema(description = "필수 여부", example = "true") boolean required,
            @Schema(description = "현재 약관 버전", example = "1.0") String version
    ) {}
}
