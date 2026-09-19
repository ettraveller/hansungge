package com.hanseongge.domain.user.dto;

import com.hanseongge.domain.user.entity.AgreementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "AgreementChoice", description = "약관 동의 선택")
public record AgreementChoice(
        @Schema(description = "약관 코드", example = "TERMS", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "약관 코드가 필요합니다.")
        AgreementType type,

        @Schema(description = "동의 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "동의 여부가 필요합니다.")
        Boolean agreed
) {}
