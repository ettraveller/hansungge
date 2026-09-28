package com.hanseongge.domain.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 결제창을 띄우기 위해 화면이 필요한 값.
 *
 * <p>{@code clientKey} 는 <b>공개해도 되는 값</b>이다 — 브라우저가 토스 SDK 를 초기화하는 데 쓴다.
 * 시크릿 키는 절대 여기 담지 않는다.
 *
 * <p>{@code available} 이 false 면 화면은 결제 버튼을 아예 그리지 않는다.
 * 눌러야만 실패를 아는 버튼을 만들지 않는다.
 */
@Schema(name = "PaymentConfigResponse", description = "결제 설정")
public record PaymentConfigResponse(

        @Schema(description = "결제를 진행할 수 있는가", example = "true") boolean available,
        @Schema(description = "토스 클라이언트 키(공개값)") String clientKey,
        @Schema(description = "테스트 키인가 — 화면에 안내를 띄운다", example = "true") boolean testMode
) {
}
