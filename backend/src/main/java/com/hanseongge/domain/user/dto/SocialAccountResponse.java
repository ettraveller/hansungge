package com.hanseongge.domain.user.dto;

import com.hanseongge.domain.user.entity.OAuthProviderType;
import com.hanseongge.domain.user.entity.SocialAccount;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

@Schema(name = "SocialAccountResponse", description = "연동된 소셜 계정")
public record SocialAccountResponse(

        @Schema(description = "제공자", example = "KAKAO") OAuthProviderType provider,
        @Schema(description = "표시명", example = "카카오") String providerName,
        @Schema(description = "연동 시점 이메일") String email,
        @Schema(description = "연동 시각") OffsetDateTime connectedAt
) {

    public static SocialAccountResponse from(SocialAccount account) {
        return new SocialAccountResponse(account.getProvider(),
                account.getProvider().displayName(), account.getEmail(), account.getConnectedAt());
    }
}
