package com.hanseongge.domain.setting.controller;

import com.hanseongge.domain.setting.service.FooterSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 푸터 글자 (공개).
 *
 * <p>푸터는 모든 화면에 있다. 그래서 로그인 전에도 열려 있어야 한다 — 막으면
 * 첫 화면의 푸터가 통째로 빈다. 담긴 것은 상호·주소·대표전화처럼 가게가 내거는
 * 공개 정보뿐이다.
 *
 * <p>관리자 화면도 <b>이것을 읽는다</b>. 같은 값을 주는 조회를 두 벌 두면 한쪽만
 * 고쳐지는 날이 온다.
 */
@Tag(name = "푸터", description = "푸터에 표시되는 회사 정보")
@RestController
@RequestMapping("/api/v1/footer")
@RequiredArgsConstructor
public class FooterController {

    private final FooterSettingService footerSettingService;

    @Operation(summary = "푸터 정보 조회",
            description = "칸 이름 → 값. 저장된 적 없는 칸은 코드의 기본값이 나간다.")
    @GetMapping
    public ResponseEntity<Map<String, String>> get() {
        return ResponseEntity.ok(footerSettingService.current());
    }
}
