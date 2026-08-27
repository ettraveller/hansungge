package com.hanseongge.domain.marketprice.entity;

/**
 * 시세 수집 시도 결과 (V7, 개발계획 §12.2 L1-b).
 *
 * <p>요구사항의 "API 오류 / timeout / 잘못된 데이터 / 중복 데이터"가 각각 어디로 가는지를
 * 이 열거형이 전부 받아낸다. <b>SUCCESS 가 아닌 모든 값은 원본 행을 만들지 않고</b>,
 * 원본이 없으면 최종 시세도 발행되지 않으므로 기존 시세가 그대로 유지된다.
 */
public enum FetchResult {

    /** 원본 저장까지 성공. 이때만 {@code raw_id} 가 채워진다. */
    SUCCESS,

    /**
     * 이미 같은 기준시각의 원본이 있어 건너뜀.
     *
     * <p>V2 스키마에는 {@code quote_at} 과 dedup UNIQUE 인덱스가 없어(결함 D-12)
     * 아직 이 값을 기록하는 경로가 없다. D-12 해소 시 연결된다.
     */
    SKIPPED_DUPLICATE,

    /** 응답 상태코드가 2xx 가 아님 */
    HTTP_ERROR,
    /** 연결·읽기 타임아웃 */
    TIMEOUT,
    /** 응답은 받았으나 해석 실패. 원문은 로그 message 에 남긴다 */
    PARSE_ERROR,
    /** 값이 범위를 벗어남 — 0 이하, 금속별 상하한 이탈, 단위(돈↔g) 오입력 의심 */
    INVALID_VALUE,
    /** 위 어디에도 해당하지 않는 예외 */
    EXCEPTION;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
