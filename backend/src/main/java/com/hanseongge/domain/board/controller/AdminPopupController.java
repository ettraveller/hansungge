package com.hanseongge.domain.board.controller;

import com.hanseongge.domain.board.dto.AdminPopupResponse;
import com.hanseongge.domain.board.dto.PopupRequest;
import com.hanseongge.domain.board.service.PopupService;
import com.hanseongge.global.auth.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 팝업.
 *
 * <p>페이지를 나누지 않는다. 팝업은 많아야 서너 개이고, 차례(sort)를 보며 손보는
 * 화면이라 전부 한눈에 보이는 편이 낫다 — 2페이지로 넘어간 팝업은 잊혀진다.
 */
@Tag(name = "관리자 팝업", description = "메인 팝업 등록·수정·끄고켜기")
@RestController
@RequestMapping("/api/admin/popups")
@RequiredArgsConstructor
public class AdminPopupController {

    private final PopupService popupService;

    @Operation(summary = "팝업 목록",
            description = "끈 것과 기간이 지난 것까지 전부. 「지금 떠 있는가」를 함께 준다.")
    @PreAuthorize("hasRole('OPERATOR')")
    @GetMapping
    public ResponseEntity<List<AdminPopupResponse>> list() {
        return ResponseEntity.ok(popupService.listForAdmin());
    }

    @Operation(summary = "팝업 등록")
    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping
    public ResponseEntity<AdminPopupResponse> create(
            @Valid @RequestBody PopupRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(popupService.create(request, actor.loginId()));
    }

    @Operation(summary = "팝업 수정")
    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<AdminPopupResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PopupRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(popupService.update(id, request, actor.loginId()));
    }

    @Operation(summary = "팝업 끄고 켜기",
            description = "기간을 지우지 않고 잠시 내릴 때 쓴다. 지우는 것보다 먼저 권한다.")
    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/{id}/active")
    public ResponseEntity<AdminPopupResponse> changeActive(
            @PathVariable Long id,
            @RequestParam boolean active,
            @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(popupService.changeActive(id, active, actor.loginId()));
    }

    @Operation(summary = "팝업 삭제",
            description = "거래 기록이 아니라 한동안 띄웠던 알림창이라 지워도 된다. "
                    + "다만 끄는 것으로 충분한 경우가 대부분이다.")
    @PreAuthorize("hasRole('MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal AdminPrincipal actor) {
        popupService.delete(id, actor.loginId());
        return ResponseEntity.noContent().build();
    }
}
