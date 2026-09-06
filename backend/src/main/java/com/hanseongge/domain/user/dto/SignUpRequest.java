package com.hanseongge.domain.user.dto;

import com.hanseongge.domain.user.entity.AgreementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

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

        /**
         * 자체 가입에서만 보낸다. 소셜 가입이면 null.
         *
         * <p>정책은 관리자 계정과 같다 — 상한 72자는 취향이 아니라 <b>BCrypt 의 한계</b>다.
         * BCrypt 는 72바이트를 넘는 입력을 조용히 잘라내므로, 그보다 긴 비밀번호는
         * 뒷부분이 아무 의미가 없는데 사용자는 그것을 모른다.
         */
        @Schema(description = "비밀번호. 자체 가입에만 필요하다")
        @Pattern(regexp = "^$|" + UserPasswordPolicy.PATTERN, message = UserPasswordPolicy.MESSAGE)
        String password
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
