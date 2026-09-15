package com.hanseongge.domain.marketprice.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 시세 버전.
 *
 * <p>값의 뜻을 해석하지 않는다 — "지난번과 다른가"만 비교한다. 지금은 가장 최근 발행 행의
 * 번호지만, 그 사실에 기대는 화면을 만들지 않도록 이름을 {@code version} 으로 둔다.
 */
@Schema(name = "PriceVersionResponse", description = "시세 버전 (바뀌었는지 비교용)")
public record PriceVersionResponse(

        @Schema(description = "시세 버전. 새로 고시되면 달라진다. 고시된 적 없으면 0", example = "1204")
        long version
) {
}
