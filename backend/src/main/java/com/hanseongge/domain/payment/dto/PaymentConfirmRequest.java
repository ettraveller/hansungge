package com.hanseongge.domain.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 승인 요청.
 *
 * <h2>금액을 받지 않는다</h2>
 * 토스는 성공 주소에 {@code amount} 도 붙여 보내지만, 그것은 <b>브라우저를 거쳐 온 값</b>이라
 * 주소창에서 고칠 수 있다. 서버는 주문번호로 DB 의 금액을 찾아 그것으로 승인한다.
 */
@Schema(name = "PaymentConfirmRequest", description = "결제 승인 (금액은 받지 않는다)")
public record PaymentConfirmRequest(

        @Schema(description = "주문번호", example = "HS20260928-000123")
        @NotBlank String orderNo,

        @Schema(description = "토스가 돌려준 거래 키")
        @NotBlank String paymentKey
) {
}
