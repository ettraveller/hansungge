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
        @Schema(description = "테스트 키인가 — 화면에 안내를 띄운다", example = "true") boolean testMode,

        /**
         * 입금받을 계좌. 설정되지 않았으면 null 이고, 그때는 무통장 주문을 받지 않는다.
         *
         * <p>주문마다 들고 다니지 않는다 — 가게의 계좌는 주문의 속성이 아니라 설정이다.
         * 주문 응답에 복사해 넣으면 계좌가 바뀐 날 지나간 주문이 옛 계좌를 계속 안내한다.
         */
        @Schema(description = "무통장입금 받을 계좌. 설정 전이면 null") BankAccount bankAccount,

        @Schema(description = "입금 안내 기한(시간). 지나도 자동취소하지 않는다", example = "3")
        int depositDueHours
) {

    /**
     * 입금받을 계좌.
     *
     * <p>공개 응답에 계좌번호가 나간다. 가게가 돈을 받으려고 <b>내거는</b> 번호이므로
     * 숨길 값이 아니다 — 홈페이지 하단이나 명함에 적히는 것과 같은 정보다.
     */
    @Schema(name = "BankAccountResponse", description = "무통장입금 계좌")
    public record BankAccount(
            @Schema(description = "은행명", example = "국민은행") String bank,
            @Schema(description = "계좌번호", example = "123456-78-901234") String number,
            @Schema(description = "예금주", example = "주식회사 금한성") String holder
    ) {
    }
}
