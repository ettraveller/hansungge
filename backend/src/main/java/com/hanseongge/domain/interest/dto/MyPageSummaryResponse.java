package com.hanseongge.domain.interest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 마이페이지 맨 위 한 줄.
 *
 * <p>마이페이지에 들어오는 가장 큰 이유가 "내 주문 어떻게 됐지"다. 그래서 메뉴를 누르기
 * 전에 숫자부터 보여준다.
 */
@Schema(name = "MyPageSummaryResponse", description = "마이페이지 요약")
public record MyPageSummaryResponse(

        @Schema(description = "회원 이름") String name,
        @Schema(description = "총 주문 건수", example = "6") long orderCount,
        @Schema(description = "결제 대기", example = "4") long awaitingPayment,
        @Schema(description = "상품 준비 중", example = "1") long preparing,
        @Schema(description = "배송 중", example = "0") long shipping,
        @Schema(description = "배송 완료", example = "1") long delivered,
        @Schema(description = "취소·환불", example = "0") long cancelled,
        @Schema(description = "찜한 상품 수", example = "3") long wishlistCount
) {
}
