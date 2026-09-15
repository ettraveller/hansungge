package com.hanseongge.domain.marketprice.controller;

import com.hanseongge.domain.marketprice.dto.PriceHistoryResponse;
import com.hanseongge.domain.marketprice.dto.PriceVersionResponse;
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
import org.springframework.http.CacheControl;
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
 *   <li><b>0원을 내보내지 않는다.</b> 고시된 적 없는 시세는 가격 자리가 {@code null} 이고
 *       {@code displayText} 가 "시세문의"다</li>
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
            description = "전 금속 × 매입/판매. 고시된 적 없는 조합은 가격 대신 '시세문의'를 돌려준다. "
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

    @Operation(summary = "시세 버전",
            description = "새로 고시되면 달라지는 숫자 하나. 열어 둔 쇼핑몰 화면이 주기적으로 물어, "
                    + "달라졌을 때만 시세를 다시 받는다. 서버에 캐시되어 DB 에 닿지 않는다.")
    @GetMapping("/version")
    public ResponseEntity<PriceVersionResponse> version() {
        // 중간 캐시가 이 응답을 붙잡으면 화면이 바뀐 것을 영영 모른다.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PriceVersionResponse(marketPriceService.currentVersion()));
    }

    @Operation(summary = "시세 이력",
            description = "지정 기간의 시세 이력을 오래된 순으로 돌려준다. 차트용이다. "
                    + "기간이 시작할 때 적용 중이던 값을 맨 앞에 포함한다 — 그 행의 발행 시각은 기간보다 앞선다. "
                    + "하루 한두 번 고시해도 짧은 기간의 그래프가 비지 않게 하기 위해서다.")
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
