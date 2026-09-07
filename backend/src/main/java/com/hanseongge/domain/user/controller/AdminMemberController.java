package com.hanseongge.domain.user.controller;

import com.hanseongge.domain.user.dto.AdminMemberDetailResponse;
import com.hanseongge.domain.user.dto.AdminMemberSummaryResponse;
import com.hanseongge.domain.user.dto.MemberReasonRequest;
import com.hanseongge.domain.user.dto.MemberStatusChangeRequest;
import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.entity.UserStatus;
import com.hanseongge.domain.user.service.AdminMemberService;
import com.hanseongge.global.auth.AdminPrincipal;
import com.hanseongge.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 회원 관리.
 *
 * <h2>권한을 세 단계로 나눈다</h2>
 * <ul>
 *   <li><b>목록</b> — {@code OPERATOR}. 값이 가려져 나가므로 조회 담당이 봐도 된다</li>
 *   <li><b>상세 · 잠금 해제</b> — {@code MANAGER}. 상세는 개인정보 원본이 나가고,
 *       잠금 해제는 고객센터가 매일 하는 일이다</li>
 *   <li><b>이용정지 · 해제</b> — {@code ADMIN}. 고객이 서비스를 못 쓰게 만드는 조치다.
 *       상품 하나 고치는 것과 무게가 다르다</li>
 * </ul>
 */
@Tag(name = "관리자 회원", description = "회원 조회 · 이용정지 · 잠금 해제")
@RestController
@RequestMapping("/api/admin/members")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATOR')")
public class AdminMemberController {

    private final AdminMemberService memberService;

    @Operation(summary = "회원 목록",
               description = "이메일·이름·휴대폰으로 검색한다. 값은 가려져서 나간다.")
    @GetMapping
    public ResponseEntity<PageResponse<AdminMemberSummaryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        // 한 응답 안에서는 같은 시각으로 잠금을 판정한다. 줄마다 now() 를 부르면
        // 경계에 걸친 회원이 같은 화면에서 잠김/해제로 갈릴 수 있다
        OffsetDateTime now = memberService.now();

        return ResponseEntity.ok(PageResponse.of(
                memberService.findPage(keyword, status, pageable),
                user -> AdminMemberSummaryResponse.of(user, now)));
    }

    @Operation(summary = "상태별 회원 수", description = "목록 상단 요약.")
    @GetMapping("/counts")
    public ResponseEntity<Map<UserStatus, Long>> counts() {
        return ResponseEntity.ok(memberService.countByStatus());
    }

    /**
     * 회원 상세.
     *
     * <p>이름·이메일·휴대폰이 <b>가려지지 않고</b> 나간다. 그래서 이 조회는 감사로그에
     * 남고, 목록보다 높은 등급을 요구한다.
     */
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "회원 상세",
               description = "개인정보 원본이 포함되며, 조회 사실이 감사로그에 남는다.")
    @GetMapping("/{id}")
    public ResponseEntity<AdminMemberDetailResponse> detail(@PathVariable Long id) {
        User user = memberService.findForDetail(id);
        return ResponseEntity.ok(AdminMemberDetailResponse.of(
                user, memberService.socialProviders(id), memberService.now()));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "회원 상태 변경",
               description = "이용정지·정지 해제·휴면 해제. 사유가 필수이며 감사로그에 남는다. "
                           + "탈퇴는 지정할 수 없고, 탈퇴 회원은 변경 대상이 아니다.")
    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminMemberDetailResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody MemberStatusChangeRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {

        User user = memberService.changeStatus(id, request.status(), request.reason(), actor.loginId());
        return ResponseEntity.ok(AdminMemberDetailResponse.of(
                user, memberService.socialProviders(id), memberService.now()));
    }

    /**
     * 로그인 실패 잠금 해제.
     *
     * <p>이용정지({@code BLOCKED})와 다르다. 잠금은 {@code ACTIVE} 인 채로 걸리고 30분 뒤
     * 스스로 풀리는데, 지금 전화를 걸어 온 고객을 기다리게 할 이유가 없어서 둔 경로다.
     */
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "잠금 해제",
               description = "연속 로그인 실패로 걸린 잠금을 즉시 푼다. 이용정지와는 별개다.")
    @PatchMapping("/{id}/unlock")
    public ResponseEntity<AdminMemberDetailResponse> unlock(
            @PathVariable Long id,
            @Valid @RequestBody MemberReasonRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {

        User user = memberService.unlock(id, request.reason(), actor.loginId());
        return ResponseEntity.ok(AdminMemberDetailResponse.of(
                user, memberService.socialProviders(id), memberService.now()));
    }
}
