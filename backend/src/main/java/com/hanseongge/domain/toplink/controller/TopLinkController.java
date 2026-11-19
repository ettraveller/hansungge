package com.hanseongge.domain.toplink.controller;

import com.hanseongge.domain.toplink.dto.TopLinkResponse;
import com.hanseongge.domain.toplink.service.TopLinkService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 상단 띠 링크 (공개).
 *
 * <p>띠는 모든 화면에 있어 로그인 전에도 열려 있어야 한다. 관리자 화면도 이것을
 * 읽는다 — 꺼 둔 링크라는 것이 없어서 손님이 보는 목록과 관리자가 고칠 목록이 같고,
 * 같은 값을 주는 조회를 두 벌 두면 한쪽만 고쳐지는 날이 온다 (푸터와 같은 이유).
 */
@Tag(name = "상단 띠", description = "헤더 위 유틸 띠의 링크")
@RestController
@RequestMapping("/api/v1/top-links")
@RequiredArgsConstructor
public class TopLinkController {

    private final TopLinkService linkService;

    @Operation(summary = "상단 띠 링크", description = "띠에 나가는 차례대로. 비어 있으면 링크를 그리지 않는다.")
    @GetMapping
    public ResponseEntity<List<TopLinkResponse>> links() {
        return ResponseEntity.ok(linkService.findAll().stream().map(TopLinkResponse::from).toList());
    }
}
