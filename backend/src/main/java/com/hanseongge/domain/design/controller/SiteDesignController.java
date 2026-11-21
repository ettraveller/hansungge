package com.hanseongge.domain.design.controller;

import com.hanseongge.domain.design.dto.SiteDesign;
import com.hanseongge.domain.design.service.SiteDesignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "화면 꾸미기", description = "메인 구역의 차례와 구역별 색")
@RestController
@RequestMapping("/api/v1/site-design")
@RequiredArgsConstructor
public class SiteDesignController {

    private final SiteDesignService siteDesignService;

    @Operation(summary = "화면 설정 조회",
            description = "저장된 적이 없으면 기본 차례와 빈 색(= 기본색)이 나간다.")
    @GetMapping
    public ResponseEntity<SiteDesign> get() {
        return ResponseEntity.ok(siteDesignService.current());
    }
}
