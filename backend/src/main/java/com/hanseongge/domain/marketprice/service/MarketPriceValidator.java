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
 *   <li><b>단위 오입력</b> — 지금 값의 1/3.75배(또는 3.75배) ±5%. 고시는 돈 단위로
 *       받으므로 "1g 가격을 1돈 칸에 넣었다"가 가장 흔하고 가장 비싼 실수다</li>
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

    /** 단위 오입력 판정 허용 오차(%). 기준 배수에서 ±5% 안에 들면 의심한다. */
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
     * 단위를 헷갈려 입력한 것으로 의심되는가.
     *
     * <p>받는 값이 돈(3.75g) 단위이므로 흔한 실수는 <b>g 가격을 돈 칸에 넣는 것</b>이다.
     * 환산을 거친 값이 지금 값의 1/3.75 로 떨어진다. 예전처럼 돈 가격을 g 칸에 넣는
     * 실수(3.75배)는 입력 단위가 바뀌어 더 이상 생기지 않지만, 외부에서 API 를 직접
     * 부르거나 환산을 두 번 적용하면 나올 수 있어 양쪽 다 본다.
     *
     * <p>범위 검사만으로는 못 잡는다 — 137,000 원/g 의 3.75배인 513,750 은 24K 상한
     * 500,000 을 겨우 넘지만, 은이나 14K 라면 상한 안에 들어와 조용히 통과한다.
     * 반대쪽도 같다. 그러면 판매가가 3.75배(또는 1/3.75)로 나가고 주문이 그대로 접수된다.
     *
     * <p><b>확인 절차로 넘기지 않고 거절한다.</b> 큰 변동은 {@code confirmLargeChange}
     * 로 넘길 수 있는데, 단위 실수를 그 길로 보내면 관리자가 확인 버튼을 눌러 통과시킨다.
     */
    private PriceValidation validateUnit(String label, PricePerGram price, PricePerGram previous) {
        if (previous == null) {
            return PriceValidation.ok();
        }
        BigDecimal ratio = price.value().divide(previous.value(), 6, RoundingMode.HALF_UP);
        BigDecimal perDon = BigDecimal.ONE.divide(MetalType.GRAMS_PER_DON, 6, RoundingMode.HALF_UP);

        String suspected = null;
        if (near(ratio, perDon)) {
            suspected = "1g 가격을 1돈(3.75g) 칸에 넣지 않았는지";
        } else if (near(ratio, MetalType.GRAMS_PER_DON)) {
            suspected = "1돈(3.75g) 환산을 두 번 적용하지 않았는지";
        }

        if (suspected != null) {
            String reason = ("%s %s원은 지금 값 %s원의 %s배입니다. %s 확인해 주세요. (1g 기준)")
                    .formatted(label, won(price.value()), won(previous.value()),
                            ratio.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),
                            suspected);
            log.warn("[MARKET-PRICE] 단위 오입력 의심 — {}", reason);
            return PriceValidation.invalid(reason);
        }
        return PriceValidation.ok();
    }

    /** {@code target} 에서 ±{@link #UNIT_ERROR_TOLERANCE_PERCENT}% 안에 들어오는가. */
    private static boolean near(BigDecimal ratio, BigDecimal target) {
        BigDecimal deviation = ratio.subtract(target).abs()
                .divide(target, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        return deviation.compareTo(UNIT_ERROR_TOLERANCE_PERCENT) <= 0;
    }

    /** 220800.00 → "220,800". DecimalFormat 은 스레드 안전하지 않아 매번 만든다. */
    static String won(BigDecimal value) {
        return new DecimalFormat("#,##0.##").format(value);
    }
}
