package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.setting.service.SystemSettingService;
import com.hanseongge.global.util.PricePerGram;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

/**
 * 시세 값 검증 — <b>양수지만 말이 안 되는 값</b>을 잡는다.
 *
 * <p>0 이하는 여기까지 오지 않는다. {@link PricePerGram} 이 생성 자체를 거부한다.
 *
 * <ol>
 *   <li><b>범위 이탈</b> — 금속별 상하한. 자릿수 실수(1,370,000 원/g)를 잡는다</li>
 *   <li><b>단위 오입력</b> — 지금 값의 3.75배 ±5%. "1돈 가격을 1g 칸에 넣었다"는
 *       가장 흔하고 가장 비싼 실수라 따로 본다</li>
 * </ol>
 *
 * <p>상하한은 {@code system_settings} 의 {@code MIN/MAX_PRICE_PER_GRAM_<금속>} 이 있으면
 * 그 값을, 없으면 {@link MetalType} 의 기본값을 쓴다.
 *
 * <p>사유 문구는 관리자 화면에 그대로 나간다. 그래서 금속 코드가 아니라 화면에 쓰는
 * 이름으로, 무엇을 고쳐야 하는지까지 적는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketPriceValidator {

    /** 단위 오입력 판정 허용 오차(%). 3.75배 ±5% 안에 들면 의심한다. */
    private static final BigDecimal UNIT_ERROR_TOLERANCE_PERCENT = new BigDecimal("5");

    private final SystemSettingService systemSettingService;

    /**
     * @param label    사유 문구에 쓸 이름. 예: "순금 24K 살 때"
     * @param previous 지금 고시가. 없으면(처음 고시) 단위 오입력 검사를 건너뛴다
     */
    public PriceValidation validate(MetalType metal, String label, PricePerGram price, PricePerGram previous) {
        PriceValidation range = validateRange(metal, label, price);
        if (!range.valid()) {
            return range;
        }
        return validateUnit(label, price, previous);
    }

    private PriceValidation validateRange(MetalType metal, String label, PricePerGram price) {
        BigDecimal min = systemSettingService.getDecimal(metal.minSettingKey(), metal.defaultMinPricePerGram());
        BigDecimal max = systemSettingService.getDecimal(metal.maxSettingKey(), metal.defaultMaxPricePerGram());

        if (price.value().compareTo(min) < 0) {
            return PriceValidation.invalid(
                    "%s %s원은 하한 %s원보다 낮습니다. 자릿수를 확인해 주세요. (1g 기준)".formatted(
                            label, won(price.value()), won(min)));
        }
        if (price.value().compareTo(max) > 0) {
            return PriceValidation.invalid(
                    "%s %s원은 상한 %s원보다 높습니다. 자릿수를 확인해 주세요. (1g 기준)".formatted(
                            label, won(price.value()), won(max)));
        }
        return PriceValidation.ok();
    }

    /**
     * 돈(3.75g) 단위 값을 g 칸에 넣은 것으로 의심되는가.
     *
     * <p>범위 검사만으로는 못 잡는다 — 137,000 원/g 의 3.75배인 513,750 은 24K 상한
     * 500,000 을 겨우 넘지만, 은이나 14K 라면 상한 안에 들어와 조용히 통과한다.
     * 그러면 판매가가 3.75배로 나가고 주문이 그대로 접수된다.
     */
    private PriceValidation validateUnit(String label, PricePerGram price, PricePerGram previous) {
        if (previous == null) {
            return PriceValidation.ok();
        }
        BigDecimal ratio = price.value().divide(previous.value(), 6, RoundingMode.HALF_UP);
        BigDecimal deviation = ratio.subtract(MetalType.GRAMS_PER_DON).abs()
                .divide(MetalType.GRAMS_PER_DON, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (deviation.compareTo(UNIT_ERROR_TOLERANCE_PERCENT) <= 0) {
            String reason = ("%s %s원은 지금 값 %s원의 %s배입니다. 1돈(3.75g) 가격을 1g 칸에 "
                    + "넣지 않았는지 확인해 주세요.").formatted(
                    label, won(price.value()), won(previous.value()),
                    ratio.setScale(2, RoundingMode.HALF_UP).toPlainString());
            log.warn("[MARKET-PRICE] 단위 오입력 의심 — {}", reason);
            return PriceValidation.invalid(reason);
        }
        return PriceValidation.ok();
    }

    /** 220800.00 → "220,800". DecimalFormat 은 스레드 안전하지 않아 매번 만든다. */
    static String won(BigDecimal value) {
        return new DecimalFormat("#,##0.##").format(value);
    }
}
