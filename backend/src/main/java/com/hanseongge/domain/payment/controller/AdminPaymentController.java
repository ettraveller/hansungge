package com.hanseongge.domain.payment.controller;

import com.hanseongge.domain.payment.dto.AdminPaymentSummaryResponse;
import com.hanseongge.domain.payment.entity.Payment;
import com.hanseongge.domain.payment.entity.PaymentStatus;
import com.hanseongge.domain.payment.repository.PaymentRepository;
import com.hanseongge.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 결제 내역 — 조회 전용.
 *
 * <h2>왜 주문관리와 따로인가</h2>
 * 주문은 "물건을 보내야 하는가"의 화면이고, 여기는 <b>"돈이 실제로 오갔는가"</b>의
 * 화면이다. 둘은 보통 일치하지만, 어긋난 날(승인은 됐는데 주문이 미결제, 실패가
 * 쌓이는 날)에 바로 이 화면이 필요하다 — PG 대시보드와 대조하는 자리다.
 *
 * <h2>쓰기가 없다</h2>
 * 결제를 승인·취소하는 길은 PG 와의 흐름(결제창·웹훅·향후 취소 API)에만 둔다.
 * 관리자 화면에서 결제 행을 직접 고치게 두면 기록과 사실이 갈라진다.
 */
@Tag(name = "관리자 결제", description = "결제 내역 조회")
@RestController
@RequestMapping("/api/admin/payments")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATOR')")
public class AdminPaymentController {

    private final PaymentRepository paymentRepository;

    @Operation(summary = "결제 내역",
            description = "결제 시도 단위의 목록이다. 한 주문에 여러 줄일 수 있다 — "
                    + "결제창에서 나갔다가 다시 시도한 흔적까지 그대로 보인다.")
    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<PageResponse<AdminPaymentSummaryResponse>> list(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {

        String orderNo = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return ResponseEntity.ok(PageResponse.of(
                paymentRepository.searchForAdmin(status, orderNo, pageable),
                row -> AdminPaymentSummaryResponse.of((Payment) row[0], (String) row[1])));
    }
}
