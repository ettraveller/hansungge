package com.hanseongge.domain.design.controller;

import com.hanseongge.domain.design.dto.SiteDesign;
import com.hanseongge.domain.design.service.SiteDesignService;
import com.hanseongge.global.auth.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 - 화면 꾸미기")
@RestController
@RequestMapping("/api/admin/site-design")
@RequiredArgsConstructor
public class AdminSiteDesignController {

    private final SiteDesignService siteDesignService;

    @Operation(summary = "화면 설정 저장",
            description = "보낸 것이 곧 전체다. 모르는 구역·색 이름은 버리고, 빠진 구역은 "
                    + "켠 채로 뒤에 붙이며, #rrggbb 가 아닌 색은 비운다(= 기본색).")
    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping
    public ResponseEntity<SiteDesign> save(
            @RequestBody SiteDesign request,
            @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(siteDesignService.save(request, actor.loginId()));
    }
}
