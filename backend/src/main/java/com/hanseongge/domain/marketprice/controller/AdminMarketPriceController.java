package com.hanseongge.domain.marketprice.controller;

import com.hanseongge.domain.marketprice.dto.CollectionStatusResponse;
import com.hanseongge.domain.marketprice.dto.CurrentPriceResponse;
import com.hanseongge.domain.marketprice.dto.PendingDecisionRequest;
import com.hanseongge.domain.marketprice.dto.PendingResponse;
import com.hanseongge.domain.marketprice.dto.PolicyResponse;
import com.hanseongge.domain.marketprice.dto.PolicyUpdateRequest;
import com.hanseongge.domain.marketprice.dto.PriceApplyResponse;
import com.hanseongge.domain.marketprice.dto.PriceDeclareRequest;
import com.hanseongge.domain.marketprice.dto.PriceHistoryResponse;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PendingStatus;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.service.CurrentPriceView;
import com.hanseongge.domain.marketprice.service.MarketPriceCollector;
import com.hanseongge.domain.marketprice.service.MarketPriceDeclarationService;
import com.hanseongge.domain.marketprice.service.MarketPricePendingService;
import com.hanseongge.domain.marketprice.service.MarketPricePolicyService;
import com.hanseongge.domain.marketprice.service.MarketPriceService;
import com.hanseongge.domain.marketprice.service.PriceApplyResult;
import com.hanseongge.global.auth.AdminPrincipal;
import com.hanseongge.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관리자 시세 API (설계서 §5, §8 시세관리 메뉴).
 *
 * <h2>권한 분리</h2>
 * 클래스는 {@code OPERATOR} 이상(조회)이고, <b>시세를 바꾸는 메서드만</b> {@code ADMIN} 이상이다.
 * 설계서 §7.4 가 "시세 적용 권한은 ADMIN 이상으로 제한한다 — 가격에 직결되는 유일한 조작"
 * 이라고 못 박은 지점이다. 권한 계층(SUPER_ADMIN &gt; ADMIN &gt; MANAGER &gt; OPERATOR)이
 * 있으므로 각 메서드는 <b>최소 등급</b>만 선언한다.
 */
@Tag(name = "관리자 시세", description = "시세 조회 / 수동 고시 / 보정정책 / 승인")
@RestController
@RequestMapping("/api/admin/prices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATOR')")
public class AdminMarketPriceController {

    private final MarketPriceService marketPriceService;
    private final MarketPriceDeclarationService declarationService;
    private final MarketPricePolicyService policyService;
    private final MarketPricePendingService pendingService;
    private final MarketPriceCollector collector;

    @Value("${app.market-price.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    // ---------- 조회 ----------

    @Operation(summary = "현재 시세 현황",
            description = "전 금속 × 매입/판매의 현재 최종가. 원본가·보정액·노후 여부를 함께 돌려준다. "
                    + "고시된 적 없는 조합도 NONE 으로 포함된다.")
    @GetMapping("/current")
    public ResponseEntity<List<CurrentPriceResponse>> current() {
        List<CurrentPriceResponse> body = marketPriceService.describeAllCurrent().stream()
                .map(this::toCurrentResponse)
                .toList();
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "시세 변경 이력",
            description = "append-only 인 market_prices_effective 를 최신순으로 읽는다. "
                    + "effectiveTo 가 null 인 행이 현재값이다.")
    @GetMapping("/history")
    public ResponseEntity<PageResponse<PriceHistoryResponse>> history(
            @RequestParam MetalType metal,
            @RequestParam(defaultValue = "SELL") PriceSide side,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(PageResponse.of(
                marketPriceService.history(metal, side, pageable), PriceHistoryResponse::from));
    }

    @Operation(summary = "수집 상태",
            description = "마지막 시도·마지막 성공 시각과 누적 실패 건수. 대시보드 경고의 근거다.")
    @GetMapping("/collection-status")
    public ResponseEntity<CollectionStatusResponse> collectionStatus() {
        return ResponseEntity.ok(CollectionStatusResponse.from(collector.status(schedulerEnabled)));
    }

    @Operation(summary = "보정 정책 목록")
    @GetMapping("/policies")
    public ResponseEntity<List<PolicyResponse>> policies() {
        return ResponseEntity.ok(policyService.findAll().stream().map(PolicyResponse::from).toList());
    }

    @Operation(summary = "승인 대기 목록",
            description = "PENDING 상태인 동안 해당 금속의 현재 시세는 직전 값이 그대로 유지된다.")
    @GetMapping("/pending")
    public ResponseEntity<PageResponse<PendingResponse>> pending(
            @RequestParam(defaultValue = "PENDING") PendingStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(PageResponse.of(
                pendingService.findByStatus(status, pageable), PendingResponse::from));
    }

    // ---------- 변경 (ADMIN 이상) ----------

    @Operation(summary = "수동 시세 고시",
            description = "원본(raw)을 만들고 보정 정책을 적용해 최종 시세를 발행한다. "
                    + "변동률이 허용치를 넘으면 발행하지 않고 승인 대기로 등록되며(HTTP 200, applied=false), "
                    + "현재 시세는 변경되지 않는다. 입력값은 최종가가 아니라 KRW/1g 원본가다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/apply")
    public ResponseEntity<PriceApplyResponse> apply(@Valid @RequestBody PriceDeclareRequest request,
                                                    @AuthenticationPrincipal AdminPrincipal actor) {
        PriceApplyResult result = declarationService.declare(
                request.metalType(), request.priceSide(), request.pricePerGram(),
                request.changeReason(), actor.loginId());
        return ResponseEntity.ok(PriceApplyResponse.from(result));
    }

    @Operation(summary = "보정 정책 변경",
            description = "이미 발행된 시세는 바뀌지 않는다. 다음 적용부터 반영된다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/policies")
    public ResponseEntity<PolicyResponse> updatePolicy(@Valid @RequestBody PolicyUpdateRequest request,
                                                       @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(PolicyResponse.from(policyService.update(
                request.metalType(), request.priceSide(), request.mode(),
                request.adjAmount(), request.adjPercent(), request.manualPrice(),
                request.changeReason(), actor.loginId())));
    }

    @Operation(summary = "승인 대기 승인",
            description = "보류됐던 값을 최종 시세로 발행한다. 변동률 재검사는 하지 않는다 — "
                    + "임계값 초과를 알고도 관리자가 승인한 것이기 때문이다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/pending/{id}/approve")
    public ResponseEntity<PriceApplyResponse> approve(@PathVariable Long id,
                                                      @Valid @RequestBody PendingDecisionRequest request,
                                                      @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(PriceApplyResponse.from(
                pendingService.approve(id, request.reason(), actor.loginId())));
    }

    @Operation(summary = "승인 대기 반려",
            description = "시세는 변경되지 않는다. 판단했다는 사실이 감사로그에 남는다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/pending/{id}/reject")
    public ResponseEntity<PendingResponse> reject(@PathVariable Long id,
                                                  @Valid @RequestBody PendingDecisionRequest request,
                                                  @AuthenticationPrincipal AdminPrincipal actor) {
        return ResponseEntity.ok(PendingResponse.from(
                pendingService.reject(id, request.reason(), actor.loginId())));
    }

    // ---------- 내부 ----------

    private CurrentPriceResponse toCurrentResponse(CurrentPriceView view) {
        if (view.effective() == null) {
            return CurrentPriceResponse.none(view.metalType(), view.priceSide());
        }
        return CurrentPriceResponse.of(view, marketPriceService.breakdown(view.effective()));
    }
}
