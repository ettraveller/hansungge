package com.hanseongge.domain.board.controller;

import com.hanseongge.domain.board.dto.PopupResponse;
import com.hanseongge.domain.board.service.PopupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 고객 화면이 띄울 팝업. */
@Tag(name = "팝업", description = "메인 팝업")
@RestController
@RequestMapping("/api/v1/popups")
@RequiredArgsConstructor
public class PublicPopupController {

    private final PopupService popupService;

    @Operation(summary = "지금 띄울 팝업",
            description = "켜져 있고 노출 기간 안인 것만 차례대로 돌려준다. "
                    + "없으면 빈 목록이다 — 팝업이 없는 것은 정상이고 오류가 아니다.")
    @GetMapping
    public ResponseEntity<List<PopupResponse>> live() {
        return ResponseEntity.ok(popupService.live());
    }
}
