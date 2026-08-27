package com.hanseongge.domain.audit.controller;

import com.hanseongge.domain.audit.dto.AuditLogResponse;
import com.hanseongge.domain.audit.dto.AuditLogSearchCondition;
import com.hanseongge.domain.audit.service.AuditLogService;
import com.hanseongge.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 감사로그 조회 API (설계서 §5 — {@code GET /api/admin/audit-logs}).
 *
 * <p>ADMIN 이상만 볼 수 있다. 감사로그에는 누가 언제 어디(IP)서 무엇을 했는지가 모여 있어
 * 그 자체가 민감정보이고, MANAGER·OPERATOR 의 업무에는 필요하지 않다.
 *
 * <p>쓰기 엔드포인트는 없다. 감사로그는 시스템이 남기는 것이지 사람이 적는 것이 아니다.
 */
@Tag(name = "감사로그", description = "관리자 행위 이력 조회 (ADMIN 이상)")
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "감사로그 검색",
            description = "행위자(actor, 부분일치) · 행위(action) · 대상 · 기간[from, to) 으로 검색한다. "
                    + "정렬은 최신순 고정이다.")
    @GetMapping
    public ResponseEntity<PageResponse<AuditLogResponse>> search(
            @ModelAttribute AuditLogSearchCondition condition,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(
                PageResponse.of(auditLogService.search(condition, pageable), AuditLogResponse::from));
    }
}
