package com.hanseongge.domain.shipment.entity;

/**
 * 택배사.
 *
 * <p>지금 쓰는 곳은 우체국택배 하나다. 그래도 값으로 둔다 — 컬럼을 비워 두면 택배사가
 * 늘어나는 날 과거 행이 어디로 보낸 것인지 알 수 없고, 송장번호 형식도 택배사마다 다르다.
 *
 * <p>{@link #trackingUrl} 은 배송조회 API 가 없어도 고객이 직접 확인할 수 있는 길이다.
 * API 키가 들어오기 전까지는 이것이 유일한 조회 수단이다.
 */
public enum Carrier {

    /**
     * 우체국택배.
     *
     * <p>조회 주소에 송장번호를 붙이지 않는다 — 우체국은 GET 파라미터로 번호를 받는
     * 공개 주소를 보장하지 않아서, 번호를 붙인 링크가 어느 날 조용히 빈 화면이 된다.
     * 조회 페이지로 보내고 번호는 화면에서 복사하게 한다.
     */
    KOREA_POST("우체국택배", "https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm", 13);

    private final String displayName;
    private final String trackingUrl;
    private final int trackingNoLength;

    Carrier(String displayName, String trackingUrl, int trackingNoLength) {
        this.displayName = displayName;
        this.trackingUrl = trackingUrl;
        this.trackingNoLength = trackingNoLength;
    }

    public String displayName() {
        return displayName;
    }

    public String trackingUrl() {
        return trackingUrl;
    }

    /**
     * 송장번호 자릿수가 맞는가.
     *
     * <p>자릿수만 본다. 체크디지트까지 검사하면 택배사가 규칙을 바꾸는 날 정상 번호가
     * 거부되고, 그때 관리자는 발송을 못 한다 — 틀린 번호를 받는 것보다 그쪽이 더 나쁘다.
     */
    public boolean isValidTrackingNo(String digitsOnly) {
        return digitsOnly != null && digitsOnly.length() == trackingNoLength;
    }
}
