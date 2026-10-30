package com.hanseongge.domain.order.dto;

import com.hanseongge.domain.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * 관리자 주문 목록 검색 조건.
 *
 * <p>모두 선택이다. 아무것도 넘기지 않으면 전체를 최신순으로 준다 — 관리자가 아침에
 * 처음 여는 화면이 빈 목록이면 안 된다.
 */
@Schema(name = "AdminOrderSearchCondition", description = "관리자 주문 검색")
public record AdminOrderSearchCondition(

        @Schema(description = "주문 상태. 비우면 전체", example = "PAID")
        OrderStatus status,

        /**
         * 발송 대기만 추리기.
         *
         * <p>상태 하나로는 표현할 수 없다 — 결제완료·준비중 둘 다이면서 송장번호가 없는
         * 것이 발송 대기다. 화면이 그 조합을 알아야 하면 백엔드와 갈라지므로 조건으로 둔다.
         */
        @Schema(description = "송장번호가 없는 결제완료·준비중만", example = "true")
        Boolean awaitingShipment,

        @Schema(description = "주문번호 또는 받는 분 이름 일부", example = "HS20261030")
        String keyword,

        @Schema(description = "주문일 시작 (이 날 포함)", example = "2026-10-01")
        LocalDate from,

        @Schema(description = "주문일 끝 (이 날 포함)", example = "2026-10-31")
        LocalDate to
) {

    public AdminOrderSearchCondition {
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
    }

    public boolean wantsAwaitingOnly() {
        return Boolean.TRUE.equals(awaitingShipment);
    }
}
