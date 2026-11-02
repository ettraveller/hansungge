package com.hanseongge.domain.policy.controller;

import com.hanseongge.domain.policy.dto.RefundPolicyResponse;
import com.hanseongge.domain.policy.service.RefundPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 환불규정 공개 조회. 쇼핑몰 안내 페이지와 주문서의 동의 구역이 쓴다.
 */
@Tag(name = "정책 문서", description = "환불규정 등 쇼핑몰 정책 문서")
@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
public class RefundPolicyController {

    private final RefundPolicyService refundPolicyService;

    @Operation(summary = "환불규정 (현재 판)",
            description = "주문서는 응답의 id 를 동의한 판으로 보낸다.")
    @GetMapping("/refund")
    public ResponseEntity<RefundPolicyResponse> refund() {
        return ResponseEntity.ok(refundPolicyService.current());
    }
}
