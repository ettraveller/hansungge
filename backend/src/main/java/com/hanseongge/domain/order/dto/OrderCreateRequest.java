package com.hanseongge.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 주문서 작성 요청.
 *
 * <h2>금액·상품을 받지 않는다</h2>
 * 무엇을 몇 개 사는지는 <b>서버의 장바구니</b>에서 읽고, 얼마인지는 서버가 그 시점 시세로
 * 다시 계산한다. 화면이 보낸 금액을 받는 자리를 만들면, 그 자리가 곧 취약점이 된다
 * (원칙 P1 — FE 가격 불신).
 */
@Schema(name = "OrderCreateRequest", description = "주문서 작성 (배송지만 받는다)")
public record OrderCreateRequest(

        @Schema(description = "받는 사람", example = "홍길동")
        @NotBlank(message = "받는 분 이름을 입력해 주세요.")
        @Size(max = 100)
        String receiverName,

        @Schema(description = "연락처", example = "010-1234-5678")
        @NotBlank(message = "연락처를 입력해 주세요.")
        @Size(max = 30)
        String receiverPhone,

        @Schema(description = "우편번호", example = "03187")
        @NotBlank(message = "우편번호를 입력해 주세요.")
        @Size(max = 10)
        String zipcode,

        @Schema(description = "기본 주소")
        @NotBlank(message = "주소를 입력해 주세요.")
        @Size(max = 200)
        String address1,

        @Schema(description = "상세 주소")
        @Size(max = 200)
        String address2,

        @Schema(description = "배송 요청사항")
        @Size(max = 300, message = "요청사항은 300자까지 입력할 수 있습니다.")
        String orderMemo
) {
}
