package com.hanseongge.domain.setting.controller;

import com.hanseongge.domain.payment.dto.PaymentConfigResponse;
import com.hanseongge.domain.setting.dto.BankAccountSettingRequest;
import com.hanseongge.domain.setting.service.SystemSettingService;
import com.hanseongge.domain.audit.service.AuditLogService;
import com.hanseongge.domain.audit.entity.ActorType;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.domain.audit.dto.AuditRecord;
import com.hanseongge.global.auth.AdminPrincipal;
import com.hanseongge.global.auth.RequestInfo;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 무통장입금 계좌 설정.
 *
 * <h2>바꾼 사실을 감사로그에 남긴다</h2>
 * 이 계좌로 고객의 돈이 들어온다. 한 줄만 바뀌어도 그날의 모든 입금이 남의 통장으로
 * 간다 — 이 시스템에서 가장 조용하게, 가장 크게 잘못될 수 있는 값이다. 그래서
 * 권한과 별개로, 누가 언제 무엇을 무엇으로 바꿨는지는 반드시 남긴다.
 *
 * <p>⚠️ 지금은 {@code ADMIN} 이상이면 바꿀 수 있다. 더 좁힐지는 미정이다
 * (2026-11-04). 좁힌다면 로그인 아이디가 아니라 역할로 막아야 한다 — 아이디를
 * 코드에 박으면 그 계정이 잠기거나(5회 실패) 이름이 바뀌는 날 아무도 계좌를
 * 고칠 수 없어 배포를 해야 한다.
 * 바뀐 값과 바꾼 사람, 접속 IP 까지 남긴다. 계좌번호는 가게가 내거는 공개 정보라
 * 가리지 않는다 — 가려 두면 정작 "언제 무엇에서 무엇으로 바뀌었나"를 확인할 수 없다.
 *
 * <h2>시스템 설정 전체를 열지 않는다</h2>
 * {@code system_settings} 에는 {@code MAX_PRICE_CHANGE_PERCENT}(시세 자동적용 허용
 * 변동률)처럼 잘못 건드리면 값이 조용히 틀어지는 값이 섞여 있다. 계좌를 넣으려고 그
 * 표를 통째로 편집 가능하게 만들면, 계좌를 고치러 들어온 사람이 시세 안전망을 끌 수
 * 있게 된다. 필요한 네 칸만 연다.
 *
 * <h2>ADMIN 이상만 바꾼다</h2>
 * 이 계좌로 고객의 돈이 들어온다. 바꿀 수 있는 사람이 많을수록, 바뀐 것을 아무도
 * 모르는 날이 온다. 바뀐 값은 {@code SystemSettingService} 가 로그에 남긴다.
 */
@Tag(name = "관리자 설정", description = "무통장입금 계좌")
@RestController
@RequestMapping("/api/admin/settings/bank-account")
@RequiredArgsConstructor
public class AdminBankAccountController {

    private static final String BANK = "BANK_ACCOUNT_BANK";
    private static final String NUMBER = "BANK_ACCOUNT_NUMBER";
    private static final String HOLDER = "BANK_ACCOUNT_HOLDER";
    private static final String DUE_HOURS = "DEPOSIT_DUE_HOURS";

    /** 기한을 아무리 길게 잡아도 일주일을 넘기지 않는다 — 금 시세가 그 사이 크게 움직인다. */
    private static final int MAX_DUE_HOURS = 168;

    private final SystemSettingService settingService;
    private final AuditLogService auditLogService;

    @Operation(summary = "입금 계좌 조회",
            description = "셋 중 하나라도 비어 있으면 무통장 주문을 받지 않는다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<Map<String, Object>> get() {
        return ResponseEntity.ok(Map.of(
                "bank", settingService.get(BANK, ""),
                "number", settingService.get(NUMBER, ""),
                "holder", settingService.get(HOLDER, ""),
                "depositDueHours", settingService.getInt(DUE_HOURS, 3)));
    }

    @Operation(summary = "입금 계좌 저장",
            description = "셋 중 하나라도 비우면 무통장입금이 꺼진다 — 계좌를 내리는 길이 그것이다. "
                    + "기한은 안내용이며, 지나도 주문을 자동으로 취소하지 않는다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping
    public ResponseEntity<PaymentConfigResponse.BankAccount> save(
            @Valid @RequestBody BankAccountSettingRequest request,
            @AuthenticationPrincipal AdminPrincipal actor,
            HttpServletRequest httpRequest) {

        // 바뀌기 전 값을 먼저 뜬다. 감사로그의 before 가 없으면 "무엇에서" 가 사라지고,
        // 남는 것은 "누가 언제 이걸로 바꿨다" 뿐이라 되돌릴 근거가 되지 못한다.
        Map<String, String> before = current();

        String bank = trimmed(request.bank());
        String number = trimmed(request.number());
        String holder = trimmed(request.holder());

        settingService.update(BANK, bank, actor.loginId());
        settingService.update(NUMBER, number, actor.loginId());
        settingService.update(HOLDER, holder, actor.loginId());

        if (request.depositDueHours() != null) {
            // 0 이하는 "이미 지난 기한"이라 안내가 성립하지 않는다.
            int hours = Math.min(Math.max(request.depositDueHours(), 1), MAX_DUE_HOURS);
            settingService.update(DUE_HOURS, String.valueOf(hours), actor.loginId());
        }

        auditLogService.record(AuditRecord.builder(AuditActions.BANK_ACCOUNT_UPDATE)
                .actor(ActorType.ADMIN, actor.loginId())
                .target(AuditActions.TARGET_SETTING, "BANK_ACCOUNT")
                .before(before)
                .after(current())
                .request(RequestInfo.from(httpRequest).ip(), RequestInfo.from(httpRequest).userAgent())
                .build());

        return ResponseEntity.ok(new PaymentConfigResponse.BankAccount(bank, number, holder));
    }

    /** 지금 설정값. 감사로그의 before/after 에 같은 모양으로 들어간다. */
    private Map<String, String> current() {
        return Map.of(
                "bank", settingService.get(BANK, ""),
                "number", settingService.get(NUMBER, ""),
                "holder", settingService.get(HOLDER, ""),
                "depositDueHours", String.valueOf(settingService.getInt(DUE_HOURS, 3)));
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
