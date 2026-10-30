package com.hanseongge.domain.shipment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.shipment.entity.Shipment;
import com.hanseongge.domain.shipment.tracking.DeliveryTrackingException;
import com.hanseongge.domain.shipment.tracking.DeliveryTrackingProvider;
import com.hanseongge.domain.shipment.tracking.TrackingResult;
import com.hanseongge.global.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 배송 중인 건의 진행 단계를 주기적으로 가져온다.
 *
 * <h2>조용히 실패한다</h2>
 * 조회가 안 되는 것은 배송의 문제가 아니다. 키가 없거나 우체국이 응답하지 않으면
 * 그냥 넘어가고, 송장번호와 조회 링크는 그대로 동작한다. 이 작업이 멈춰도 고객은
 * 직접 확인할 수 있고 관리자는 손으로 수령을 기록할 수 있다.
 *
 * <h2>한 건의 실패가 나머지를 막지 않는다</h2>
 * 반송·주소불명처럼 조회가 영영 안 되는 번호가 섞여 있을 수 있다. 하나가 터질 때마다
 * 배치를 중단하면 그 뒤에 줄 선 정상 건들이 영영 갱신되지 않는다.
 *
 * <h2>수령을 확정하지 않는다</h2>
 * 택배사가 "배달완료"라고 해도 {@code deliveredAt} 을 쓰지 않는다. 고가 귀금속의 수령
 * 확인은 사람이 남기는 기록이어야 분쟁에서 근거가 된다 (이용약관 제10조).
 * 대신 관리자 화면이 그 상태를 보여주고, 사람이 「배송완료 처리」를 누른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingSyncJob {

    private final DeliveryTrackingProvider provider;
    private final ShipmentService shipmentService;
    private final TrackingApplier applier;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    /**
     * {@code fixedDelay} 다 — 앞 실행이 끝난 뒤부터 센다. {@code fixedRate} 로 두면 한 번이
     * 느려졌을 때 다음 실행이 겹쳐 같은 건을 두 번 묻고, 하루 호출 한도를 그만큼 더 쓴다.
     *
     * <p>{@code initialDelay} 를 두는 이유 — 기동 직후에는 Flyway 와 캐시 예열이 겹친다.
     * 급할 것 없는 작업이라 비켜 준다.
     */
    /**
     * 첫 실행인가.
     *
     * <p>첫 번째만 결과와 상관없이 INFO 로 남긴다. 그러지 않으면 조회할 건이 없는 동안
     * 로그가 비는데, 그 모습이 <b>"작업이 아예 안 돈다"와 똑같다.</b> 한 줄이라도 찍혀야
     * 스케줄러가 살아 있다는 것을 확인할 수 있다.
     */
    private boolean firstRun = true;

    @Scheduled(fixedDelayString = "${app.tracking.sync-interval-ms:900000}", initialDelay = 120_000)
    public void sync() {
        if (!provider.isConfigured()) {
            return;   // 키가 없다. 기동 때 한 번 알렸으므로 여기서는 조용히 넘어간다
        }

        var config = appProperties.tracking();
        OffsetDateTime staleBefore =
                OffsetDateTime.now().minusMinutes(config.syncIntervalMinutes());

        List<Shipment> due = shipmentService.findDueForTracking(staleBefore, config.batchSize());
        if (due.isEmpty()) {
            if (firstRun) {
                firstRun = false;
                log.info("[TRACKING] 동기화 작업 시작 — 지금 조회할 건이 없다 "
                        + "(발송됨 + {}분 넘게 조회 안 한 건이 대상)", config.syncIntervalMinutes());
            }
            return;
        }
        firstRun = false;

        int ok = 0;
        int failed = 0;
        for (Shipment shipment : due) {
            try {
                TrackingResult result = provider.track(shipment.getCarrier(), shipment.getTrackingNo());
                applier.apply(shipment.getId(), result.externalStatus(),
                        toJson(result.steps()), result.inTransit());
                ok++;
            } catch (DeliveryTrackingException e) {
                // 한 건이 안 된다고 나머지를 막지 않는다. 다음 주기에 다시 묻는다.
                failed++;
                log.debug("[TRACKING] 조회 실패 shipmentId={} : {}", shipment.getId(), e.getMessage());
            } catch (Exception e) {
                failed++;
                log.warn("[TRACKING] 예상 못 한 실패 shipmentId={}", shipment.getId(), e);
            }
        }

        log.info("[TRACKING] 동기화 대상={} 성공={} 실패={}", due.size(), ok, failed);
    }

    /**
     * 진행 단계를 JSON 문자열로.
     *
     * <p>실패하면 null 이다 — 단계를 저장하지 못하는 것 때문에 상태 갱신까지 버리면,
     * 화면이 계속 옛 상태를 보여준다.
     */
    private String toJson(List<TrackingResult.Step> steps) {
        if (steps.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (Exception e) {
            log.debug("[TRACKING] 단계를 JSON 으로 쓰지 못했다: {}", e.getMessage());
            return null;
        }
    }
}
