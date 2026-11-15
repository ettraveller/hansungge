package com.hanseongge.domain.banner.controller;

import com.hanseongge.domain.banner.dto.HeroBannerResponse;
import com.hanseongge.domain.banner.service.HeroBannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "메인 배너")
@RestController
@RequestMapping("/api/v1/banners")
@RequiredArgsConstructor
public class PublicHeroBannerController {

    private final HeroBannerService bannerService;

    @Operation(summary = "메인 배너",
            description = "내걸린 것만 진열 차례대로. 비어 있으면 화면이 기본 배너를 쓴다.")
    @GetMapping
    public ResponseEntity<List<HeroBannerResponse>> banners() {
        return ResponseEntity.ok(bannerService.findActive().stream()
                .map(banner -> HeroBannerResponse.from(banner, bannerService.contentOf(banner)))
                .toList());
    }
}
