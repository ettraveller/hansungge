package com.hanseongge.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 주문 관리 화면 상태 탭의 미확인 건수.
 *
 * <p>미확인 = 관리자가 상세를 아직 열지 않은 주문 ({@code admin_checked_at IS NULL}).
 * 숫자의 뜻은 <b>그 탭을 눌렀을 때 나오는 목록 중 미확인인 줄 수</b>다 — 탭 필터와
 * 같은 기준으로 세지 않으면 배지 숫자와 목록이 어긋난다.
 *
 * <p>필드가 화면의 탭 구성(발송 대기·전체·결제완료·배송중·배송완료·취소)과 1:1 이다.
 * 탭이 늘면 여기도 는다 — 화면이 조합해 만들게 두면 세는 기준이 둘로 갈라진다.
 */
@Schema(name = "AdminOrderUncheckedCounts", description = "상태 탭별 미확인 주문 건수")
public record AdminOrderUncheckedCounts(

        @Schema(description = "발송 대기 탭 — 결제완료·준비중 + 송장번호 없음") long awaiting,
        @Schema(description = "전체 탭") long all,
        @Schema(description = "결제완료 탭") long paid,
        @Schema(description = "배송중 탭") long shipped,
        @Schema(description = "배송완료 탭") long delivered,
        @Schema(description = "취소 탭") long cancelled
) {
}
