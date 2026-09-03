package com.hanseongge.domain.marketprice.provider;

import com.hanseongge.domain.marketprice.entity.MetalType;
import com.hanseongge.global.util.PricePerGram;

import java.time.OffsetDateTime;

/**
 * Provider 가 가져온 원본 시세 1건.
 *
 * <p>가격 타입이 {@link PricePerGram} 인 것이 핵심이다. 이 타입은 0 이하 값의 생성을
 * 거부하므로, <b>0원짜리 원본은 이 객체 자체가 만들어지지 않는다.</b> 원칙 P6("외부 시스템
 * 실패가 가격을 0·NULL 로 만드는 경로는 존재하지 않는다")이 타입 수준에서 강제된다.
 *
 * <p>{@code quotedAt}(시세 기준시각)은 V2 스키마에 대응 컬럼이 없어(결함 D-12 관련)
 * 아직 {@code raw_payload} 에 함께 실어 보존한다.
 *
 * @param metalType  금속
 * @param price      KRW/1g. 돈 단위 원천이면 Provider 가 환산해서 넘긴다
 * @param quotedAt   시세 <b>기준</b> 시각 (수집 시각과 다를 수 있다)
 * @param payloadJson 응답 원문 JSON. 분쟁 시 근거. null 허용
 */
public record RawPrice(
        MetalType metalType,
        PricePerGram price,
        OffsetDateTime quotedAt,
        String payloadJson
) {

    public RawPrice {
        if (metalType == null || price == null) {
            throw new IllegalArgumentException("metalType 과 price 는 필수입니다");
        }
        if (quotedAt == null) {
            quotedAt = OffsetDateTime.now();
        }
    }
}
