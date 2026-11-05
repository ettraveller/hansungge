package com.hanseongge.domain.product.entity;

/**
 * 상품마다 정하는 배송 고지 (V41).
 *
 * <p>두 값을 하나로 묶어 다닌다. 둘 다 짧은 문자열이라 따로 넘기면 등록·수정 경로의
 * 인자 목록에 같은 자리에 같은 타입이 늘어서고, 그런 자리는 <b>순서를 바꿔 넣어도
 * 컴파일된다</b> — 그날 상품 상세에 택배사 자리에 기간이 찍힌다.
 *
 * <h2>NULL 은 "안 정했다"는 뜻이다</h2>
 * 화면이 기본 문구를 쓴다 — 기간은 「상품별 상이 · 상세페이지 참조」, 택배사는
 * 「우체국택배」. 기본 문구를 여기 두지 않는 이유는 그 문장이 이용약관·이용안내와
 * 같은 말을 해야 하기 때문이다. 출처는 프런트의 {@code content/delivery.ts} 하나다.
 *
 * <p>공백 문자열은 NULL 로 접는다. 그대로 저장하면 화면에 <b>빈 칸</b>이 찍혀,
 * 고지가 없는 것인지 비워 둔 것인지 구분할 수 없다.
 */
public record DeliveryNotice(String period, String courier) {

    /** 아무것도 정하지 않음. 화면이 기본 문구를 쓴다. */
    public static final DeliveryNotice NONE = new DeliveryNotice(null, null);

    public DeliveryNotice {
        period = blankToNull(period);
        courier = blankToNull(courier);
    }

    /** null 요청(이 필드를 보내지 않는 호출)도 "안 정했다"로 받는다. */
    public static DeliveryNotice of(String period, String courier) {
        return new DeliveryNotice(period, courier);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
