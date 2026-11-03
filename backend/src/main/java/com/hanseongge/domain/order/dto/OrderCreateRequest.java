package com.hanseongge.domain.order.dto;

import com.hanseongge.domain.order.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        String orderMemo,

        /**
         * 청약철회 제한 안내에 동의했는가.
         *
         * <p>안내만으로는 제한이 성립하지 않는다 — 전자상거래법 제17조 제2항 단서는
         * 사업자가 그 사실을 소비자가 쉽게 알 수 있는 곳에 <b>명기</b>하도록 요구하고,
         * 분쟁에서는 "이 주문에서 동의했다" 를 보여야 한다. 그래서 받고, 기록한다.
         */
        @Schema(description = "청약철회 제한 안내 동의", requiredMode = Schema.RequiredMode.REQUIRED)
        @AssertTrue(message = "청약철회 제한 안내에 동의해야 주문할 수 있습니다.")
        boolean withdrawalConsent,

        /**
         * 동의한 환불규정 판 번호 ({@code GET /api/v1/policies/refund} 의 id).
         *
         * <p>"동의했다" 만으로는 부족하다 — 분쟁에서는 <b>어느 내용에</b> 동의했는지를
         * 보여야 한다. 읽는 사이 규정이 개정됐으면 서버가 409 로 거절하고 다시 읽게 한다.
         */
        @Schema(description = "동의한 환불규정 판 번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "환불규정 동의 정보가 없습니다.")
        Long refundPolicyId,

        /**
         * 결제수단.
         *
         * <p>화면이 고르지만 <b>허용 여부는 서버가 정한다.</b> 장바구니에 카드로 팔 수
         * 없는 상품(순금·순은·골드바·24K·시세 연동)이 하나라도 있으면 {@code EASY_PAY}
         * 요청을 거절한다 — 받아 주면 심사 위반 거래가 PG 로 나간다.
         *
         * <p>비워 두면 {@code EASY_PAY} 로 본다. 이 칸이 생기기 전의 화면이 아직
         * 떠 있을 수 있어서다. 그 경우에도 위 검사가 그대로 걸린다.
         */
        @Schema(description = "EASY_PAY(카드·간편결제) / BANK_TRANSFER(무통장입금)",
                example = "EASY_PAY")
        PaymentMethod paymentMethod,

        /**
         * 입금자명. 무통장일 때만 쓴다.
         *
         * <p>주문자 이름과 다를 수 있다 — 남편·자녀 이름으로 부치는 일이 흔하다.
         * 이 값이 없으면 들어온 돈이 어느 주문의 것인지 사람이 찾아야 한다.
         */
        @Schema(description = "입금자명 (무통장입금일 때 필수)", example = "홍길동")
        @Size(max = 60, message = "입금자명은 60자까지 입력할 수 있습니다.")
        String depositorName
) {
}
