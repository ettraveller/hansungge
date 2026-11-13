package com.hanseongge.domain.banner.controller;

import com.hanseongge.domain.banner.dto.HeroBannerReplaceRequest;
import com.hanseongge.domain.banner.dto.HeroBannerResponse;
import com.hanseongge.domain.banner.service.HeroBannerService;
import com.hanseongge.global.auth.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "관리자 - 메인 배너")
@RestController
@RequestMapping("/api/admin/banners")
@RequiredArgsConstructor
public class AdminHeroBannerController {

    private final HeroBannerService bannerService;

    @Operation(summary = "메인 배너 목록", description = "꺼 둔 것까지 진열 차례대로.")
    @PreAuthorize("hasRole('OPERATOR')")
    @GetMapping
    public ResponseEntity<List<HeroBannerResponse>> list() {
        return ResponseEntity.ok(bannerService.findAll().stream()
                .map(banner -> HeroBannerResponse.from(banner, bannerService.contentOf(banner)))
                .toList());
    }

    @Operation(summary = "메인 배너 교체",
            description = "보낸 것이 곧 전체다. 한 장씩 더하고 지우지 않는다 — 그 사이에 "
                    + "반쯤 섞인 배너가 손님 화면에 걸린다. 빈 배열이면 배너를 모두 내린다.")
    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping
    public ResponseEntity<List<HeroBannerResponse>> replace(
            @Valid @RequestBody HeroBannerReplaceRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {

        return ResponseEntity.ok(bannerService.replace(request, actor.loginId()).stream()
                .map(banner -> HeroBannerResponse.from(banner, bannerService.contentOf(banner)))
                .toList());
    }
}
