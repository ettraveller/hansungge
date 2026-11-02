package com.hanseongge.domain.policy.controller;

import com.hanseongge.domain.policy.dto.RefundPolicyPublishRequest;
import com.hanseongge.domain.policy.dto.RefundPolicyVersionResponse;
import com.hanseongge.domain.policy.service.RefundPolicyService;
import com.hanseongge.global.auth.AdminPrincipal;
import com.hanseongge.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 환불규정 관리.
 *
 * <p>공지(OPERATOR)보다 높은 ADMIN 전용이다. 이 문서는 모든 주문의 거래 조건이라,
 * 잘못 고치면 그 뒤의 모든 분쟁에 영향을 준다.
 */
@Tag(name = "관리자 환불규정", description = "환불규정 조회 · 개정 이력 · 새 판 발행")
@RestController
@RequestMapping("/api/admin/policies/refund")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundPolicyController {

    private final RefundPolicyService refundPolicyService;

    @Operation(summary = "현재 판")
    @GetMapping
    public ResponseEntity<RefundPolicyVersionResponse> current() {
        return ResponseEntity.ok(refundPolicyService.currentForAdmin());
    }

    @Operation(summary = "개정 이력", description = "최신 판부터. 지난 판은 고칠 수 없다.")
    @GetMapping("/history")
    public ResponseEntity<PageResponse<RefundPolicyVersionResponse>> history(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(refundPolicyService.history(pageable));
    }

    @Operation(summary = "새 판 발행",
            description = "저장하면 쇼핑몰과 주문서에 바로 보인다. 지난 판은 그대로 남는다 — "
                    + "이미 만들어진 주문들이 동의한 내용이기 때문이다.")
    @PostMapping
    public ResponseEntity<RefundPolicyVersionResponse> publish(
            @Valid @RequestBody RefundPolicyPublishRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(refundPolicyService.publish(request, actor));
    }
}
