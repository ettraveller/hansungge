package com.hanseongge.domain.marketprice.service;

/**
 * 시세 값 검증 결과.
 *
 * @param reason 실패 사유. 관리자 화면에 그대로 나가므로 사람 말로 적는다. 통과면 null
 */
public record PriceValidation(boolean valid, String reason) {

    private static final PriceValidation OK = new PriceValidation(true, null);

    public static PriceValidation ok() {
        return OK;
    }

    public static PriceValidation invalid(String reason) {
        return new PriceValidation(false, reason);
    }
}
