package com.hanseongge.global.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 부가세 정책 계산 (TODO A-1 확정 전 임시 동작 포함).
 *
 * <p>세 모드의 차이가 <b>최종 금액을 바꾸는가</b>에 있다는 것이 핵심이다.
 * {@code EXCLUDED} 만 금액이 커지고, 나머지는 소계가 곧 합계다.
 */
class VatModeTest {

    private static final BigDecimal RATE_10 = BigDecimal.TEN;

    @Test
    @DisplayName("NONE — 세액 0, 최종 = 소계. A-1 확정 전 임시 기본값이다")
    void none() {
        Money subtotal = Money.of("837500");

        Money vat = VatMode.NONE.vatOf(subtotal, RATE_10);
        assertThat(vat.value()).isEqualByComparingTo("0.00");
        assertThat(VatMode.NONE.totalOf(subtotal, vat).value()).isEqualByComparingTo("837500.00");

        assertThat(VatMode.TEMPORARY_DEFAULT).isEqualTo(VatMode.NONE);
    }

    @Test
    @DisplayName("INCLUDED — 표시가에 포함. 세액은 소계×10/110, 최종은 소계 그대로")
    void included() {
        Money subtotal = Money.of("110000");

        Money vat = VatMode.INCLUDED.vatOf(subtotal, RATE_10);
        assertThat(vat.value()).isEqualByComparingTo("10000.00");
        // 금액이 커지지 않는다 — 이미 들어 있던 세액을 분리해 보여줄 뿐이다
        assertThat(VatMode.INCLUDED.totalOf(subtotal, vat).value()).isEqualByComparingTo("110000.00");
    }

    @Test
    @DisplayName("EXCLUDED — 별도 부과. 세액은 소계×10%, 최종은 소계+세액")
    void excluded() {
        Money subtotal = Money.of("100000");

        Money vat = VatMode.EXCLUDED.vatOf(subtotal, RATE_10);
        assertThat(vat.value()).isEqualByComparingTo("10000.00");
        assertThat(VatMode.EXCLUDED.totalOf(subtotal, vat).value()).isEqualByComparingTo("110000.00");
    }

    @Test
    @DisplayName("나누어떨어지지 않아도 전(錢) 단위까지 정확하다")
    void rounding() {
        Money subtotal = Money.of("837500");

        // 837,500 × 10/110 = 76,136.3636...
        assertThat(VatMode.INCLUDED.vatOf(subtotal, RATE_10).value())
                .isEqualByComparingTo("76136.36");
        // 837,500 × 10% = 83,750
        assertThat(VatMode.EXCLUDED.vatOf(subtotal, RATE_10).value())
                .isEqualByComparingTo("83750.00");
    }

    @Test
    @DisplayName("미확정('TBD')·오타·공백은 파싱되지 않는다 — 호출자가 임시 정책을 선택하게 한다")
    void parseRejectsUndecided() {
        assertThat(VatMode.parse("TBD")).isEmpty();
        assertThat(VatMode.parse("")).isEmpty();
        assertThat(VatMode.parse(null)).isEmpty();
        assertThat(VatMode.parse("included")).contains(VatMode.INCLUDED);
        assertThat(VatMode.parse(" EXCLUDED ")).contains(VatMode.EXCLUDED);
    }
}
