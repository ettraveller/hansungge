package com.hanseongge.domain.shipment.tracking;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 배송조회 결과.
 *
 * <p>택배사마다 응답 모양이 다르다. 그 차이는 각 구현이 흡수하고, 이 안쪽은 어느
 * 택배사인지 모른 채로 돌아간다.
 *
 * @param externalStatus 택배사가 쓰는 상태 문자열 그대로. 우리가 해석하지 않은 원본이라
 *                       나중에 "그때 뭐라고 왔었나"를 따질 수 있다
 * @param inTransit      아직 가는 중인가. 이 값만 우리 상태에 반영한다
 * @param delivered      택배사 기준으로 수령됐는가. <b>이것으로 수령을 확정하지 않는다</b> —
 *                       관리자에게 "완료 처리하라"고 알리는 신호로만 쓴다
 * @param steps          진행 단계. 화면이 위에서 아래로 그린다
 */
public record TrackingResult(
        String externalStatus,
        boolean inTransit,
        boolean delivered,
        List<Step> steps
) {

    public TrackingResult {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    /**
     * 한 단계.
     *
     * @param at    그 일이 일어난 시각
     * @param where 어디서. 택배사가 주지 않으면 null
     * @param what  무슨 일이. "집화완료", "배송출발" 같은 택배사의 표현 그대로
     */
    public record Step(OffsetDateTime at, String where, String what) {}
}
