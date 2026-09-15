package com.hanseongge.domain.marketprice.controller;

import com.hanseongge.domain.marketprice.dto.CurrentPriceResponse;
import com.hanseongge.domain.marketprice.dto.DerivationPreviewRequest;
import com.hanseongge.domain.marketprice.dto.DerivationResponse;
import com.hanseongge.domain.marketprice.dto.DerivationUpdateRequest;
import com.hanseongge.domain.marketprice.dto.DerivationUpdateResponse;
import com.hanseongge.domain.marketprice.dto.PriceDeclarePairRequest;
import com.hanseongge.domain.marketprice.dto.PriceHistoryResponse;
import com.hanseongge.domain.marketprice.dto.PricePairPreviewRequest;
import com.hanseongge.domain.marketprice.dto.QuotePlanResponse;
import com.hanseongge.domain.marketprice.dto.QuotePublishResponse;
import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.service.MarketPriceDeclarationService;
import com.hanseongge.domain.marketprice.service.MarketPricePolicyService;
import com.hanseongge.domain.marketprice.service.MarketPriceService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 관리자 시세 API (설계서 §5, §8 시세관리 메뉴).
 *
 * <h2>시세는 관리자만 만든다</h2>
 * 외부 시세를 받아 오지 않는다. 시세가 생기는 길은 두 가지뿐이다 — 관리자가 살 때·팔 때를
 * 고시하거나, 18K·14K 를 24K 에서 환산하도록 설정하는 것.
 *
 * <h2>고시는 두 번 부른다</h2>
 * 먼저 {@code preview} 로 무엇이 얼마로 바뀌는지 받고, 확인 기준을 넘는 값이 있으면 화면이
 * 확인을 받은 뒤 {@code confirmLargeChange: true} 로 발행한다. 확인 없이 기준을 넘는 발행은
 * 409 로 거절되고 아무것도 저장되지 않는다.
 *
 * <h2>권한 분리</h2>
 * 클래스는 {@code OPERATOR} 이상(조회)이고, <b>시세를 바꾸는 메서드만</b> {@code ADMIN} 이상이다.
 * 설계서 §7.4 가 "시세 적용 권한은 ADMIN 이상으로 제한한다 — 가격에 직결되는 유일한 조작"
 * 이라고 못 박은 지점이다.
 */
@Tag(name = "관리자 시세", description = "시세 조회 / 고시 / 18K·14K 환산 설정")
@RestController
@RequestMapping("/api/admin/prices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATOR')")
public class AdminMarketPriceController {

    private final MarketPriceService marketPriceService;
    private final MarketPriceDeclarationService declarationService;
    private final MarketPricePolicyService policyService;

    // ---------- 조회 ----------

    @Operation(summary = "현재 시세 현황",
            description = "전 금속 × 살 때/팔 때의 현재 고시가와 직전 대비 변동폭. "
                    + "고시된 적 없는 조합도 NONE 으로 포함된다.")
    @GetMapping("/current")
    public ResponseEntity<List<CurrentPriceResponse>> current() {
        // 직전 시세를 한 번에 읽어 둔다. 줄마다 조회하면 표 한 번에 10 쿼리가 나간다.
        Map<String, MarketPriceEffective> previous = marketPriceService.findPreviousByKey();

        return ResponseEntity.ok(marketPriceService.describeAllCurrent().stream()
                .map(view -> CurrentPriceResponse.of(view,
                        previous.get(MarketPriceService.previousKey(view.metalType(), view.priceSide()))))
                .toList());
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

    @Operation(summary = "18K·14K 고시 방식",
            description = "직접 입력인지 24K 환산인지, 환산이면 비율이 얼마인지.")
    @GetMapping("/derivations")
    public ResponseEntity<List<DerivationResponse>> derivations() {
        return ResponseEntity.ok(policyService.findDerivations().stream()
                .map(DerivationResponse::from)
                .toList());
    }

    // ---------- 변경 (ADMIN 이상) ----------

    @Operation(summary = "고시 미리보기",
            description = "고시하면 무엇이 얼마로 바뀌는지 계산만 한다. 24K 면 환산으로 설정된 "
                    + "18K·14K 도 함께 나온다. needsConfirm 이 true 면 직전 대비 확인 기준을 넘는 값이 있다. "
                    + "값이 검증에 걸리면 발행과 똑같은 400 이 나간다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/apply-pair/preview")
    public ResponseEntity<QuotePlanResponse> previewPair(@Valid @RequestBody PricePairPreviewRequest request) {
        return ResponseEntity.ok(QuotePlanResponse.from(declarationService.preview(
                request.metalType(), request.sellPricePerGram(), request.buyPricePerGram())));
    }

    @Operation(summary = "시세 고시 (살 때·팔 때 동시)",
            description = "한 금속의 살 때와 팔 때를 함께 고시한다. 입력한 값이 곧 고시가다. "
                    + "24K 면 환산으로 설정된 18K·14K 가 같은 발행 시각으로 함께 나간다. "
                    + "살 때가 팔 때보다 크지 않으면 400. 확인 기준을 넘는 값이 있는데 "
                    + "confirmLargeChange 가 true 가 아니면 409 이고 아무것도 저장하지 않는다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/apply-pair")
    public ResponseEntity<QuotePublishResponse> applyPair(
            @Valid @RequestBody PriceDeclarePairRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {

        return ResponseEntity.ok(QuotePublishResponse.from(declarationService.declareBoth(
                request.metalType(), request.sellPricePerGram(), request.buyPricePerGram(),
                request.changeReason(), actor.loginId(),
                Boolean.TRUE.equals(request.confirmLargeChange()))));
    }

    @Operation(summary = "환산 비율 미리보기",
            description = "이 비율로 바꾸면 18K·14K 가 지금 24K 기준으로 얼마가 되는지. "
                    + "24K 가 아직 고시되지 않았으면 lines 가 비어 있다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/derivations/preview")
    public ResponseEntity<QuotePlanResponse> previewDerivation(
            @Valid @RequestBody DerivationPreviewRequest request) {

        return ResponseEntity.ok(QuotePlanResponse.from(
                policyService.previewDerivation(request.metalType(), request.ratio())));
    }

    @Operation(summary = "18K·14K 고시 방식 변경",
            description = "24K 환산으로 바꾸거나 비율을 고치면 지금 24K 기준으로 곧바로 다시 발행한다. "
                    + "직접 입력으로 돌리면 마지막 값이 그대로 남는다. "
                    + "다시 나갈 값이 확인 기준을 넘는데 confirmLargeChange 가 true 가 아니면 409 이고 "
                    + "설정도 시세도 바뀌지 않는다.")
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/derivations")
    public ResponseEntity<DerivationUpdateResponse> updateDerivation(
            @Valid @RequestBody DerivationUpdateRequest request,
            @AuthenticationPrincipal AdminPrincipal actor) {

        return ResponseEntity.ok(DerivationUpdateResponse.from(policyService.updateDerivation(
                request.metalType(), request.mode(), request.ratio(),
                request.changeReason(), actor.loginId(),
                Boolean.TRUE.equals(request.confirmLargeChange()))));
    }
}
