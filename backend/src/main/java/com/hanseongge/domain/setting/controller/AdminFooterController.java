package com.hanseongge.domain.setting.controller;

import com.hanseongge.domain.setting.service.FooterSettingService;
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

import java.util.Map;

/**
 * 푸터 글자 저장.
 *
 * <h2>조회는 두지 않는다</h2>
 * 공개 조회({@code GET /api/v1/footer})가 같은 값을 준다. 관리자용 조회를 따로 두면
 * 두 벌이 되고, 칸을 더하는 날 한쪽만 고쳐진다.
 *
 * <h2>MANAGER 이상만 바꾼다</h2>
 * 상호·사업자등록번호·사업장 소재지는 전자상거래법 §13 이 표시하라는 값이다. 틀린
 * 값이 올라가면 고객이 읽는 자리에 틀린 법정 표시가 걸린다 — 배너 사진을 바꾸는 일과
 * 같은 무게가 아니다.
 *
 * <p>누가 무엇을 무엇으로 바꿨는지는 {@code SystemSettingService} 가 칸마다 로그에
 * 남긴다. 계좌 설정처럼 감사로그까지 남기지는 않는다 — 여기 값은 가게가 내거는
 * 공개 정보이고, 틀리면 화면을 보면 바로 보인다.
 */
@Tag(name = "관리자 설정", description = "푸터 회사 정보")
@RestController
@RequestMapping("/api/admin/settings/footer")
@RequiredArgsConstructor
public class AdminFooterController {

    private final FooterSettingService footerSettingService;

    @Operation(summary = "푸터 정보 저장",
            description = "칸 이름 → 값. 보내지 않은 칸은 그대로 둔다. "
                    + "빈 값으로 보낸 칸은 지운 것으로 보고, 기본값이 있으면 기본값으로 돌아간다.")
    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping
    public ResponseEntity<Map<String, String>> save(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(footerSettingService.save(request, actor.loginId()));
    }
}
