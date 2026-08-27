package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.domain.setting.service.SystemSettingService;
import com.hanseongge.global.util.PricePerGram;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 원본 시세 값 검증 (개발계획 §12.4 "비정상 가격").
 *
 * <p>0 이하는 여기까지 오지 않는다 — {@link PricePerGram} 이 생성 자체를 거부한다.
 * 이 클래스가 잡는 것은 <b>양수지만 말이 안 되는 값</b>이다.
 *
 * <ol>
 *   <li><b>범위 이탈</b> — 금속별 상하한. 자릿수 실수(1,370,000 원/g)를 잡는다</li>
 *   <li><b>단위 오입력</b> — 직전값의 3.75배 ±5%. "1돈 가격을 1g 칸에 넣었다"는
 *       가장 흔하고 가장 비싼 실수라 별도로 본다</li>
 * </ol>
 *
 * <p>상하한은 {@code system_settings} 의 {@code MIN/MAX_PRICE_PER_GRAM_<금속>} 이 있으면
 * 그 값을, 없으면 {@link MetalType} 의 기본값을 쓴다. 설정 행을 미리 심어두지 않은 것은
 * 의도적이다 — 시세 범위는 시간이 지나면 반드시 바뀌는 값이라, 마이그레이션에 박아두면
 * 갱신이 배포 작업이 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketPriceValidator {

    /** 단위 오입력 판정 허용 오차(%). 3.75배 ±5% 안에 들면 의심한다. */
    private static final BigDecimal UNIT_ERROR_TOLERANCE_PERCENT = new BigDecimal("5");

    private final SystemSettingService systemSettingService;

    /**
     * @param previous 직전 최종 시세. 없으면(최초 고시) 단위 오입력 검사를 건너뛴다
     */
    public PriceValidation validate(MetalType metal, PricePerGram price, PricePerGram previous) {
        PriceValidation range = validateRange(metal, price);
        if (!range.valid()) {
            return range;
        }
        return validateUnit(metal, price, previous);
    }

    private PriceValidation validateRange(MetalType metal, PricePerGram price) {
        BigDecimal min = systemSettingService.getDecimal(metal.minSettingKey(), metal.defaultMinPricePerGram());
        BigDecimal max = systemSettingService.getDecimal(metal.maxSettingKey(), metal.defaultMaxPricePerGram());

        if (price.value().compareTo(min) < 0) {
            return PriceValidation.invalid(
                    "%s 시세가 하한 미만입니다: %s < %s (원/g)".formatted(
                            metal.name(), price.value().toPlainString(), min.toPlainString()));
        }
        if (price.value().compareTo(max) > 0) {
            return PriceValidation.invalid(
                    "%s 시세가 상한 초과입니다: %s > %s (원/g)".formatted(
                            metal.name(), price.value().toPlainString(), max.toPlainString()));
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
    private PriceValidation validateUnit(MetalType metal, PricePerGram price, PricePerGram previous) {
        if (previous == null) {
            return PriceValidation.ok();
        }
        BigDecimal ratio = price.value().divide(previous.value(), 6, RoundingMode.HALF_UP);
        BigDecimal deviation = ratio.subtract(MetalType.GRAMS_PER_DON).abs()
                .divide(MetalType.GRAMS_PER_DON, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        if (deviation.compareTo(UNIT_ERROR_TOLERANCE_PERCENT) <= 0) {
            String reason = ("%s 시세가 직전값의 %s배입니다. 1돈(3.75g) 가격을 1g 칸에 입력했는지 "
                    + "확인하세요. 직전 %s → 입력 %s (원/g)").formatted(
                    metal.name(), ratio.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    previous.value().toPlainString(), price.value().toPlainString());
            log.warn("[MARKET-PRICE] 단위 오입력 의심 — {}", reason);
            return PriceValidation.invalid(reason);
        }
        return PriceValidation.ok();
    }
}
