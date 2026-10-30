package com.hanseongge.domain.order.controller;

import com.hanseongge.domain.order.dto.AdminOrderSearchCondition;
import com.hanseongge.domain.order.dto.AdminOrderSummaryResponse;
import com.hanseongge.domain.order.dto.OrderResponse;
import com.hanseongge.domain.order.service.AdminOrderService;
import com.hanseongge.domain.shipment.dto.ShipRequest;
import com.hanseongge.domain.shipment.dto.ShipmentResponse;
import com.hanseongge.domain.shipment.service.ShipmentService;
import com.hanseongge.global.auth.AdminPrincipal;
import com.hanseongge.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 관리자 주문 · 배송 API.
 *
 * <h2>DELETE 가 없다</h2>
 * 주문은 거래 기록이고 전자상거래법 §6 이 5년 보존을 요구한다. 취소는 상태로
 * 표현하고, 행은 지우지 않는다 — 상품에 DELETE 를 두지 않은 것과 같은 이유다.
 *
 * <h2>상태를 되감는 길도 없다</h2>
 * 발송한 주문을 "준비 중"으로 되돌리는 버튼을 두지 않는다. 물건이 이미 나갔는데
 * 기록만 되감으면 그 기록이 사실과 달라진다. 잘못 보냈으면 회수·환불로 처리한다.
 * 다만 <b>송장번호는 고칠 수 있다</b> — 번호를 잘못 적는 일은 실제로 생기고,
 * 그때 상태까지 되감게 하는 것이 더 위험하다.
 */
@Tag(name = "관리자 주문", description = "주문 조회 · 배송 처리")
@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATOR')")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;
    private final ShipmentService shipmentService;

    // ---------- 조회 ----------

    @Operation(summary = "주문 목록",
            description = "조건은 모두 선택이다. awaitingShipment=true 면 송장번호가 없는 "
                    + "결제완료·준비중 주문만 추린다 — 그것이 발송 대기다.")
    @GetMapping
    public ResponseEntity<PageResponse<AdminOrderSummaryResponse>> list(
            @ModelAttribute AdminOrderSearchCondition condition,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(
                adminOrderService.search(condition, pageable), row -> row));
    }

    @Operation(summary = "발송 대기 건수",
            description = "목록 상단에 늘 보여 준다. 판정 기준은 목록 필터와 같다.")
    @GetMapping("/awaiting-count")
    public ResponseEntity<Map<String, Long>> awaitingCount() {
        return ResponseEntity.ok(Map.of("count", adminOrderService.countAwaitingShipment()));
    }

    @Operation(summary = "주문 상세",
            description = "고객이 보는 것과 같은 응답 모양이다. 금액 계산이 둘로 갈라지지 않도록.")
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> detail(@PathVariable Long id) {
        return ResponseEntity.ok(adminOrderService.detail(id));
    }

    @Operation(summary = "배송 정보",
            description = "아직 발송 전이면 status=PENDING 으로 채워 보낸다 — 화면이 널 검사를 하지 않도록.")
    @GetMapping("/{id}/shipment")
    public ResponseEntity<ShipmentResponse> shipment(@PathVariable Long id) {
        adminOrderService.requireOrder(id);   // 없는 주문에 빈 배송을 돌려주지 않는다
        return ResponseEntity.ok(shipmentService.find(id)
                .map(ShipmentResponse::from)
                .orElseGet(ShipmentResponse::pending));
    }

    // ---------- 배송 처리 (MANAGER 이상) ----------

    @Operation(summary = "상품 준비 중으로",
            description = "결제 완료 주문에만 쓴다. 배송 행은 아직 만들지 않는다.")
    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/prepare")
    public ResponseEntity<OrderResponse> prepare(@PathVariable Long id,
                                                 @AuthenticationPrincipal AdminPrincipal actor) {
        shipmentService.markPreparing(id, actor.loginId());
        return ResponseEntity.ok(adminOrderService.detail(id));
    }

    @Operation(summary = "송장번호 입력 · 발송 처리",
            description = "주문이 배송중으로 바뀌고 고객 화면에 송장번호가 보인다. "
                    + "이미 발송된 주문에 다시 보내면 송장번호만 바뀐다 — 발송 시각은 처음 값을 지킨다. "
                    + "같은 번호가 다른 주문에 쓰였으면 409.")
    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/{id}/shipment")
    public ResponseEntity<ShipmentResponse> ship(@PathVariable Long id,
                                                 @Valid @RequestBody ShipRequest request,
                                                 @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(ShipmentResponse.from(shipmentService.ship(
                id, request.carrier(), request.trackingNo(), actor.loginId())));
    }

    @Operation(summary = "배송완료 처리",
            description = "수령 기록을 남긴다. 고가 귀금속이라 분실·수령거부를 다툴 때 근거가 된다 "
                    + "(이용약관 제10조). 택배사 응답이 아니라 사람이 남긴다.")
    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping("/{id}/deliver")
    public ResponseEntity<ShipmentResponse> deliver(@PathVariable Long id,
                                                    @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(ShipmentResponse.from(
                shipmentService.markDelivered(id, actor.loginId())));
    }
}
