package com.hanseongge.domain.toplink.controller;

import com.hanseongge.domain.toplink.dto.TopLinkReplaceRequest;
import com.hanseongge.domain.toplink.dto.TopLinkResponse;
import com.hanseongge.domain.toplink.service.TopLinkService;
import com.hanseongge.global.auth.AdminPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 상단 띠 링크 저장. 조회는 공개 쪽({@code GET /api/v1/top-links})을 같이 쓴다.
 *
 * <p>MANAGER 이상만 바꾼다 — 여기 넣은 주소는 모든 화면 맨 위에서 손님을 밖으로
 * 내보내는 문이다. 배너·푸터를 고치는 권한과 같은 무게로 둔다.
 */
@Tag(name = "관리자 - 상단 띠")
@RestController
@RequestMapping("/api/admin/top-links")
@RequiredArgsConstructor
public class AdminTopLinkController {

    private final TopLinkService linkService;

    @Operation(summary = "상단 띠 링크 교체",
            description = "보낸 것이 곧 전체이고, 보낸 차례가 띠에 나가는 차례다. "
                    + "빈 배열이면 링크를 모두 내린다.")
    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping
    public ResponseEntity<List<TopLinkResponse>> replace(
            @Valid @RequestBody TopLinkReplaceRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {

        return ResponseEntity.ok(linkService.replace(request, actor.loginId()).stream()
                .map(TopLinkResponse::from)
                .toList());
    }
}
