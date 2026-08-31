package com.hanseongge.domain.marketprice.controller;

import com.hanseongge.domain.marketprice.dto.PriceHistoryResponse;
import com.hanseongge.domain.marketprice.dto.PublicPriceResponse;
import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.service.MarketPriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 공개 시세 API (설계서 §5 공개 — {@code /api/v1/prices/**}).
 *
 * <h2>이 컨트롤러가 절대 하지 않는 것</h2>
 * <ol>
 *   <li><b>0원을 내보내지 않는다.</b> 시세가 없거나 노후면 가격 자리가 {@code null} 이고
 *       {@code displayText} 가 "시세문의"다</li>
 *   <li><b>노후된 값을 그대로 내보내지 않는다.</b> 관리자 화면과 달리 여기서는 걸러진다 —
 *       고객은 화면에 뜬 가격으로 주문하려 하기 때문이다</li>
 *   <li><b>회사 마진을 노출하지 않는다.</b> 원본가·보정액·적용자는 공개 응답에 없다</li>
 * </ol>
 * 유효 시세가 없을 때 <b>500 이나 404 가 아니라 200 + "시세문의"</b>인 것도 의도적이다
 * (개발계획 §12.4, 시나리오 S6). 시세가 없는 것은 장애가 아니라 정상적으로 발생하는 상태다.
 */
@Tag(name = "시세", description = "공개 시세 조회")
@RestController
@RequestMapping("/api/v1/prices")
@RequiredArgsConstructor
@Validated
public class PublicMarketPriceController {

    /** 이력 조회 최대 기간. 무제한 조회는 전건 스캔이 된다. */
    private static final int MAX_HISTORY_DAYS = 365;

    private final MarketPriceService marketPriceService;

    @Operation(summary = "현재 시세표",
            description = "전 금속 × 매입/판매. 유효 시세가 없거나 노후면 가격 대신 '시세문의'를 돌려준다. "
                    + "이 응답에 0원이 담기는 경로는 존재하지 않는다.")
    @GetMapping("/current")
    public ResponseEntity<List<PublicPriceResponse>> current() {
        // 직전 시세를 한 번에 읽어 둔다. 줄마다 조회하면 시세표 한 번에 10 쿼리가 나간다.
        Map<String, MarketPriceEffective> previous = marketPriceService.findPreviousByKey();

        return ResponseEntity.ok(marketPriceService.describeAllCurrent().stream()
                .map(view -> PublicPriceResponse.of(
                        view,
                        previous.get(MarketPriceService.previousKey(view.metalType(), view.priceSide()))))
                .toList());
    }

    @Operation(summary = "시세 이력",
            description = "지정 기간의 최종 시세 변경 이력을 오래된 순으로 돌려준다. 차트용이다.")
    @GetMapping("/history")
    public ResponseEntity<List<PriceHistoryResponse>> history(
            @RequestParam MetalType metal,
            @RequestParam(defaultValue = "SELL") PriceSide side,
            @RequestParam(defaultValue = "30")
            @Min(value = 1, message = "조회 기간은 1일 이상이어야 합니다.")
            @Max(value = MAX_HISTORY_DAYS, message = "조회 기간은 365일을 넘을 수 없습니다.")
            int days) {

        return ResponseEntity.ok(marketPriceService.historySince(metal, side, days).stream()
                .map(PriceHistoryResponse::from)
                .toList());
    }
}
