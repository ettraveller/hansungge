package com.hanseongge.domain.marketprice.controller;

import com.hanseongge.domain.marketprice.dto.AutoQuoteStartRequest;
import com.hanseongge.domain.marketprice.dto.CollectionStatusResponse;
import com.hanseongge.domain.marketprice.dto.CurrentPriceResponse;
import com.hanseongge.domain.marketprice.dto.PendingBatchRequest;
import com.hanseongge.domain.marketprice.dto.PendingDecisionRequest;
import com.hanseongge.domain.marketprice.dto.PendingResponse;
import com.hanseongge.domain.marketprice.dto.PolicyResponse;
import com.hanseongge.domain.marketprice.dto.PolicyUpdateRequest;
import com.hanseongge.domain.marketprice.dto.PriceApplyPairResponse;
import com.hanseongge.domain.marketprice.dto.PriceApplyResponse;
import com.hanseongge.domain.marketprice.dto.PriceDeclarePairRequest;
import com.hanseongge.domain.marketprice.dto.QuoteModeRequest;
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

    @Operation(summary = "수동 시세 고시 (살 때·팔 때 동시)",
            description = "한 금속의 살 때와 팔 때를 함께 고시한다. 한쪽만 고시하는 경로를 "
                    + "두지 않는 이유는, 그 사이에 한쪽만 값이 있는 시세표가 화면에 걸리기 "
                    + "때문이다. 살 때가 팔 때보다 크지 않으면 400 으로 거절한다. "
                    + "한쪽이라도 변동률 허용치를 넘으면 둘 다 발행하지 않는다(409).")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/apply-pair")
    public ResponseEntity<PriceApplyPairResponse> applyPair(
            @Valid @RequestBody PriceDeclarePairRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {

        return ResponseEntity.ok(PriceApplyPairResponse.from(declarationService.declareBoth(
                request.metalType(), request.sellPricePerGram(), request.buyPricePerGram(),
                request.changeReason(), actor.loginId())));
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

    @Operation(summary = "바뀐 보정값을 지금 시세에 다시 적용",
            description = "가장 최근 원본에 현재 보정 정책을 적용해 새로 발행한다. "
                    + "과거 행은 그대로 두고 새 행을 쌓으므로 이력은 온전하다. "
                    + "원본이 노후 한계보다 오래되었으면 거절한다 — 오래된 값을 다시 "
                    + "발행하면 방금 고시한 것처럼 보여 낡은 가격으로 팔리게 된다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/reapply")
    public ResponseEntity<PriceApplyPairResponse> reapply(
            @Valid @RequestBody PendingDecisionRequest request,
            @RequestParam MetalType metal,
            @AuthenticationPrincipal AdminPrincipal actor) {

        return ResponseEntity.ok(PriceApplyPairResponse.from(
                marketPriceService.reapplyCurrentPolicy(metal, request.reason(), actor.loginId())));
    }

    @Operation(summary = "자동고시 시작 (보정값 포함)",
            description = "한 금속의 살 때·팔 때 보정값을 정하면서 자동고시를 켠다. "
                    + "값과 전환이 한 트랜잭션이라, 값을 정하지 않은 채 자동이 켜지는 "
                    + "상태를 만들 수 없다. 보정 0 으로 켜지면 수집값이 그대로 판매가가 "
                    + "되고, 마진 없이 파는 상태는 판 뒤에야 드러난다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/policies/auto-quote")
    public ResponseEntity<List<PolicyResponse>> startAutoQuote(
            @Valid @RequestBody AutoQuoteStartRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {

        return ResponseEntity.ok(policyService.startAutoQuote(
                        request.metalType(), request.mode(), request.sellAdj(), request.buyAdj(),
                        request.changeReason(), actor.loginId())
                .stream().map(PolicyResponse::from).toList());
    }

    @Operation(summary = "자동/수동 고시 전환 (금속 단위)",
            description = "한 금속의 살 때·팔 때를 함께 전환한다. 한쪽만 자동으로 둘 수 "
                    + "없는 이유는, 그러면 금값이 움직일 때 그쪽만 움직여 시세표가 "
                    + "어긋나기 때문이다. 자동이어도 변동률 임계를 넘으면 양쪽 모두 "
                    + "발행되지 않고 승인 대기로 간다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/policies/quote-mode")
    public ResponseEntity<List<PolicyResponse>> switchQuoteMode(
            @Valid @RequestBody QuoteModeRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {

        return ResponseEntity.ok(policyService.switchQuoteMode(
                        request.metalType(), request.autoQuote(),
                        request.changeReason(), actor.loginId())
                .stream().map(PolicyResponse::from).toList());
    }

    @Operation(summary = "승인 대기 일괄 승인 (살 때·팔 때 함께)",
            description = "한 금속의 두 건을 한 트랜잭션에서 승인한다. 한 건씩 승인하면 "
                    + "그 사이에 한쪽만 새 값인 시세표가 화면에 걸린다. "
                    + "하나라도 실패하면 전부 되돌아간다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/pending/approve")
    public ResponseEntity<List<PriceApplyResponse>> approveAll(
            @Valid @RequestBody PendingBatchRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {

        return ResponseEntity.ok(
                pendingService.approveAll(request.ids(), request.reason(), actor.loginId())
                        .stream().map(PriceApplyResponse::from).toList());
    }

    @Operation(summary = "승인 대기 일괄 반려 (살 때·팔 때 함께)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/pending/reject")
    public ResponseEntity<List<PendingResponse>> rejectAll(
            @Valid @RequestBody PendingBatchRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {

        return ResponseEntity.ok(
                pendingService.rejectAll(request.ids(), request.reason(), actor.loginId())
                        .stream().map(PendingResponse::from).toList());
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
