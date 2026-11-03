package com.hanseongge.domain.setting.controller;

import com.hanseongge.domain.payment.dto.PaymentConfigResponse;
import com.hanseongge.domain.setting.dto.BankAccountSettingRequest;
import com.hanseongge.domain.setting.service.SystemSettingService;
import com.hanseongge.global.auth.AdminPrincipal;
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
            @AuthenticationPrincipal AdminPrincipal actor) {

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

        return ResponseEntity.ok(new PaymentConfigResponse.BankAccount(bank, number, holder));
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
