package com.hanseongge.domain.user.dto;

import com.hanseongge.domain.user.entity.AgreementType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 약관별 <b>현재</b> 동의 상태.
 *
 * <p>{@code user_agreements} 는 append-only 라 한 약관에 여러 행이 쌓인다. 여기 담기는
 * 것은 그중 마지막 행이다.
 */
@Schema(name = "AgreementStatusResponse", description = "약관 동의 현황")
public record AgreementStatusResponse(

        @Schema(description = "약관 코드", example = "MARKETING") AgreementType type,
        @Schema(description = "표시 문구") String name,
        @Schema(description = "필수 여부") boolean required,
        @Schema(description = "현재 동의 여부") boolean agreed,
        @Schema(description = "동의한 약관 버전") String version,
        @Schema(description = "마지막 변경 시각") OffsetDateTime agreedAt
) {}
