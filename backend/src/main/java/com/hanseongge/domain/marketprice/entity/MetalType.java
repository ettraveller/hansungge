package com.hanseongge.domain.marketprice.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 금속 종류. V2 마이그레이션의 CHECK 제약과 값이 일치해야 한다.
 *
 * <p>저장 단위는 <b>KRW / 1g 고정</b>이다 (개발계획 §12.2). 관리자는 돈(3.75g) 단위로
 * 고시하고, {@link #perGramFromDon} 이 그 입력을
 * 저장 <i>전에</i> g 로 환산한다 — 두 단위가 같은 컬럼에 섞이면 어느 행이 어느 단위인지
 * 사후에 판별할 방법이 없다.
 *
 * <p>{@code defaultMin/MaxPricePerGram} 은 <b>범위 검증용 안전망</b>이지 시세가 아니다.
 * 자릿수 실수(1,370,000 을 137,000 대신 입력)나 단위 오입력을 걸러내기 위한 넉넉한
 * 상하한이며, 운영 중 조정이 필요하면 {@code system_settings} 의
 * {@code MIN_PRICE_PER_GRAM_<금속>} / {@code MAX_PRICE_PER_GRAM_<금속>} 이 우선한다.
 */
public enum MetalType {

    GOLD_24K("순금 24K", 50_000, 500_000),
    GOLD_18K("18K", 30_000, 400_000),
    GOLD_14K("14K", 20_000, 300_000),
    SILVER("은", 500, 20_000),
    PLATINUM("백금", 20_000, 300_000);

    /** 1돈 = 3.75g. 단위 오입력 감지의 기준이기도 하다. */
    public static final BigDecimal GRAMS_PER_DON = new BigDecimal("3.75");

    private final String displayName;
    private final BigDecimal defaultMinPricePerGram;
    private final BigDecimal defaultMaxPricePerGram;

    MetalType(String displayName, long defaultMin, long defaultMax) {
        this.displayName = displayName;
        this.defaultMinPricePerGram = BigDecimal.valueOf(defaultMin);
        this.defaultMaxPricePerGram = BigDecimal.valueOf(defaultMax);
    }

    public String displayName() {
        return displayName;
    }

    public BigDecimal defaultMinPricePerGram() {
        return defaultMinPricePerGram;
    }

    public BigDecimal defaultMaxPricePerGram() {
        return defaultMaxPricePerGram;
    }

    /**
     * 돈 단위 입력을 g 로 환산한다. <b>단위가 바뀌는 지점은 이 메서드 하나다.</b>
     *
     * <p>관리자는 돈 단위로 고시한다 — 이 업종의 시세가 그렇게 돌아다니기 때문이다.
     * 저장은 계속 원/g 이다. 상품 중량이 g 이고, 두 단위가 같은 컬럼에 섞이면 어느
     * 행이 어느 단위인지 사후에 판별할 방법이 없다.
     *
     * <p>소수점 두 자리로 맞추는 이유 — {@code market_prices_effective.price_per_gram} 이
     * {@code numeric(15,2)} 다. 여기서 반올림하지 않으면 DB 가 조용히 하고, 그러면
     * 검증이 본 값과 저장된 값이 달라진다.
     *
     * <p>662,000 처럼 3.75 로 나누어떨어지지 않는 값은 자리를 잃는다. g 값에서 버리는
     * 최대치가 0.005 원이므로 돈으로 되돌릴 때의 오차는 <b>최대 ±0.01875 원/돈</b>이다
     * (662,000 → 176,533.33 → 661,999.9875). {@link #perDonFromGram} 이 원 단위로
     * 반올림해 돌려주므로 화면에서는 고시한 값 그대로 보인다.
     */
    public static BigDecimal perGramFromDon(BigDecimal pricePerDon) {
        if (pricePerDon == null) {
            return null;
        }
        return pricePerDon.divide(GRAMS_PER_DON, 2, RoundingMode.HALF_UP);
    }

    /**
     * 저장된 g 값을 화면용 돈 값으로 되돌린다. <b>돈 값을 만드는 지점은 이 메서드 하나다.</b>
     *
     * <p>원 단위로 반올림한다. 곱셈 결과를 그대로 두면 {@code 661,999.9875} 같은 값이
     * API 에 실려 나가는데, 관리자가 방금 662,000 을 고시했으므로 그 숫자를 보면 자기가
     * 잘못 넣었는지 의심하게 된다. 시세는 원 단위로 고시하므로 소수를 남길 이유도 없다.
     *
     * <p>차액(변동폭)에도 같은 것을 쓴다 — 기준값과 단위·자리가 어긋나면 "지금 값 +
     * 변동액"이 "새 값"과 맞지 않는다.
     */
    public static BigDecimal perDonFromGram(BigDecimal pricePerGram) {
        if (pricePerGram == null) {
            return null;
        }
        return pricePerGram.multiply(GRAMS_PER_DON).setScale(0, RoundingMode.HALF_UP);
    }

    /** {@code MIN_PRICE_PER_GRAM_GOLD_24K} 형태의 설정 키. */
    public String minSettingKey() {
        return "MIN_PRICE_PER_GRAM_" + name();
    }

    public String maxSettingKey() {
        return "MAX_PRICE_PER_GRAM_" + name();
    }
}
