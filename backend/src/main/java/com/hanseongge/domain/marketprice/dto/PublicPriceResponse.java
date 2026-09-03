package com.hanseongge.domain.marketprice.dto;

import com.hanseongge.domain.marketprice.entity.MarketPriceEffective;
import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.marketprice.entity.PriceSide;
import com.hanseongge.domain.marketprice.service.CurrentPriceView;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 공개 시세표 한 줄.
 *
 * <h2>관리자 응답과 다른 점</h2>
 * <ol>
 *   <li><b>사용할 수 없는 시세는 가격을 아예 담지 않는다.</b> 노후된 값이라도 관리자에게는
 *       보여줘야 하지만, 고객에게 보이면 그 가격으로 주문하려 한다</li>
 *   <li><b>원본가·보정액·적용자를 내보내지 않는다.</b> 회사의 마진 정책이다</li>
 *   <li>가격이 없을 때 {@code 0} 이 아니라 {@code null} + {@link #displayText} 를 준다.
 *       0 은 화면에서 "0원"으로 그려질 수 있고, 그것이 이 시스템이 가장 피해야 할 표시다</li>
 * </ol>
 */
@Schema(name = "PublicPriceResponse", description = "공개 시세 (시세문의 폴백 포함)")
public record PublicPriceResponse(

        @Schema(description = "금속", example = "GOLD_24K")
        MetalType metalType,

        @Schema(description = "금속 표시명", example = "순금 24K")
        String metalName,

        @Schema(description = "매입/판매", example = "SELL")
        PriceSide priceSide,

        @Schema(description = "구분 표시명", example = "판매")
        String sideName,

        @Schema(description = "시세 사용 가능 여부", example = "true")
        boolean available,

        @Schema(description = "1g 시세(원). 사용 불가면 null", example = "210000.00")
        BigDecimal pricePerGram,

        @Schema(description = "1돈(3.75g) 시세(원). 사용 불가면 null", example = "787500.00")
        BigDecimal pricePerDon,

        @Schema(description = "화면 표시 문구. 사용 불가면 '시세문의'", example = "210,000")
        String displayText,

        @Schema(description = "시세 기준 시각. 사용 불가면 null")
        OffsetDateTime effectiveFrom,

        @Schema(description = "직전 고시 대비 변동폭. 최초 고시이거나 사용 불가면 null")
        PriceChange change
) {

    /** 유효 시세가 없거나 노후일 때의 표시 문구 (개발계획 §12.4). */
    public static final String UNAVAILABLE_TEXT = "시세문의";

    /** 직전 시세를 모르는 자리에서 쓰는 형태. 변동폭 없이 현재값만 담는다. */
    public static PublicPriceResponse of(CurrentPriceView view) {
        return of(view, null);
    }

    /**
     * @param previous 같은 metal×side 의 직전 최종 시세 행. 없으면 null
     */
    public static PublicPriceResponse of(CurrentPriceView view, MarketPriceEffective previous) {
        MetalType metal = view.metalType();
        PriceSide side = view.priceSide();

        if (!view.isSellable()) {
            // 사용할 수 없는 시세에는 변동폭도 담지 않는다. 가격이 "시세문의"인데
            // 옆에 "-3.00%" 가 붙으면 값이 있다는 뜻으로 읽힌다.
            return new PublicPriceResponse(metal, metal.displayName(), side, side.displayName(),
                    false, null, null, UNAVAILABLE_TEXT, null, null);
        }

        BigDecimal perGram = view.effective().getPricePerGram();
        return new PublicPriceResponse(
                metal, metal.displayName(), side, side.displayName(),
                true,
                perGram,
                perGram.multiply(MetalType.GRAMS_PER_DON),
                perGram.toPlainString(),
                view.effective().getEffectiveFrom(),
                PriceChange.of(perGram, previous));
    }
}
