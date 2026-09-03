package com.hanseongge.domain.user.dto;

import com.hanseongge.domain.user.entity.AgreementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(name = "SignUpRequest", description = "약관 동의 후 가입 완료")
public record SignUpRequest(

        @Schema(description = "콜백에서 받은 가입 진행 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "가입 진행 토큰이 없습니다.")
        String signUpToken,

        @Schema(description = "약관별 동의 여부. 필수 약관이 하나라도 빠지면 400",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "약관 동의 내역이 필요합니다.")
        @Valid
        List<AgreementChoice> agreements,

        @Schema(description = "표시 이름. 비우면 소셜에서 받은 이름을 쓴다", example = "홍길동")
        @Size(max = 100)
        String name
) {

    @Schema(name = "AgreementChoice", description = "약관 동의 선택")
    public record AgreementChoice(
            @Schema(description = "약관 코드", example = "TERMS", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "약관 코드가 필요합니다.")
            AgreementType type,

            @Schema(description = "동의 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull(message = "동의 여부가 필요합니다.")
            Boolean agreed
    ) {}
}
