package com.hanseongge.domain.user.dto;

import com.hanseongge.domain.user.entity.AgreementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "AgreementUpdateRequest", description = "선택 약관 동의/철회")
public record AgreementUpdateRequest(

        @Schema(description = "약관 코드. 필수 약관은 철회할 수 없다", example = "MARKETING",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "약관 코드가 필요합니다.")
        AgreementType type,

        @Schema(description = "동의 여부", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "동의 여부가 필요합니다.")
        Boolean agreed
) {}
