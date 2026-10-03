package com.hanseongge.domain.order.controller;

import com.hanseongge.domain.order.dto.DeliveryAddressRequest;
import com.hanseongge.domain.order.dto.DeliveryAddressResponse;
import com.hanseongge.domain.order.service.DeliveryAddressService;
import com.hanseongge.global.auth.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 배송지 주소록. 전 구간 로그인 필요.
 *
 * <p>주문은 주소를 복사해 가므로, 여기를 고치거나 지워도 지나간 주문의 배송지는 그대로다.
 */
@Tag(name = "배송지", description = "저장해 둔 배송지 목록 / 등록 / 수정 / 삭제")
@RestController
@RequestMapping("/api/v1/me/addresses")
@RequiredArgsConstructor
public class DeliveryAddressController {

    private final DeliveryAddressService service;

    @Operation(summary = "배송지 목록", description = "기본 배송지가 맨 위, 그다음은 최근에 넣은 순.")
    @GetMapping
    public ResponseEntity<List<DeliveryAddressResponse>> list(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(service.list(principal.id()));
    }

    @Operation(summary = "배송지 등록",
            description = "첫 배송지는 요청과 무관하게 기본 배송지가 된다.")
    @PostMapping
    public ResponseEntity<DeliveryAddressResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DeliveryAddressRequest request) {
        return ResponseEntity.ok(service.create(principal.id(), request));
    }

    @Operation(summary = "배송지 수정")
    @PutMapping("/{id}")
    public ResponseEntity<DeliveryAddressResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody DeliveryAddressRequest request) {
        return ResponseEntity.ok(service.update(principal.id(), id, request));
    }

    @Operation(summary = "기본 배송지로 지정")
    @PostMapping("/{id}/default")
    public ResponseEntity<Void> makeDefault(@AuthenticationPrincipal UserPrincipal principal,
                                            @PathVariable Long id) {
        service.makeDefault(principal.id(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "배송지 삭제",
            description = "기본 배송지를 지우면 남은 것 중 맨 위가 기본이 된다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable Long id) {
        service.delete(principal.id(), id);
        return ResponseEntity.noContent().build();
    }
}
