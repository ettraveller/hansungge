package com.hanseongge.domain.admin.dto;

import com.hanseongge.domain.admin.service.AdminOtpService;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * OTP 등록 시작 응답. 화면은 {@code otpauthUri} 로 QR 을 그리고, QR 을 찍을 수 없는 휴대폰
 * (관리 화면을 그 휴대폰으로 연 경우)을 위해 키를 글자로도 보여준다.
 */
@Schema(name = "AdminOtpSetupResponse", description = "OTP 등록 정보")
public record AdminOtpSetupResponse(

        @Schema(description = "인증 앱에 직접 입력할 키 (4자리씩 띄움)", example = "JBSW Y3DP EHPK 3PXP")
        String secret,

        @Schema(description = "QR 코드로 그릴 주소")
        String otpauthUri,

        @Schema(description = "인증 앱에 표시될 서비스 이름")
        String issuer,

        @Schema(description = "인증 앱에 표시될 계정 이름")
        String accountName
) {

    public static AdminOtpSetupResponse from(AdminOtpService.Setup setup) {
        return new AdminOtpSetupResponse(setup.secret(), setup.otpauthUri(), setup.issuer(), setup.accountName());
    }
}
