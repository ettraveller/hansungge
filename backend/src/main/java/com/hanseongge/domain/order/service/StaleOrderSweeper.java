package com.hanseongge.domain.order.service;

import com.hanseongge.domain.order.entity.Order;
import com.hanseongge.domain.order.entity.OrderStatus;
import com.hanseongge.domain.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 결제하지 않고 떠난 주문을 접고 재고를 돌려준다 (2026-09-29).
 *
 * <h2>왜 필요한가</h2>
 * 재고는 <b>주문서를 만들 때</b> 가져간다 — 결제 승인에서 빼면 마지막 하나를 두 사람이
 * 모두 결제할 수 있기 때문이다 ({@link OrderService} 참조). 대신 결제하지 않고 떠난 주문이
 * 재고를 계속 물고 있게 된다.
 *
 * <p>골드바 재고가 10개인데 결제할 생각 없이 10번 주문하면 <b>아무도 살 수 없게 된다.</b>
 * 사람이 하나씩 취소해 주기 전까지. 그래서 시간이 지난 것은 스스로 접는다.
 *
 * <h2>시간을 넉넉히 잡는 이유</h2>
 * 고객이 결제창에서 카드 정보를 넣고 있는데 주문이 취소되면, 그 뒤에 도착한 승인은
 * <b>갈 곳이 없다</b> — 돈은 빠져나갔는데 주문은 취소된 상태가 된다. 토스 결제창의 유효시간이
 * 10분이라 30분이면 충분히 안전하다.
 *
 * <h2>무통장입금은 기준이 다르다</h2>
 * 위의 고정 시간을 쓰지 않고 <b>주문마다 안내한 입금 기한</b>({@code deposit_due_at})을
 * 본다. 은행에 가서 돈을 부치는 일은 카드 결제와 걸리는 시간이 비교가 안 되고, 무엇보다
 * 고객 화면에 기한이 날짜·시각으로 찍혀 있다. 화면에 적힌 기한과 실제로 접히는 시각이
 * 다르면 설명할 방법이 없다.
 *
 * <p>자세한 것은 {@code OrderRepository.findStale} 주석 참조.
 *
 * <p>그래도 그 일이 일어나면 {@code PaymentService.settle} 이 ERROR 로 크게 남긴다.
 * 사람이 환불해야 한다.
 *
 * <h2>접는 일은 다른 빈이 한다</h2>
 * 같은 클래스 안에서 부른 {@code @Transactional} 은 프록시를 타지 않는다.
 * 자세한 것은 {@link StaleOrderExpirer} 주석 참조.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleOrderSweeper {

    /** 한 번에 처리할 최대 건수. 밀려 있어도 한 번에 다 잡으면 그 시간 동안 DB 가 묶인다. */
    private static final int BATCH = 200;

    private final OrderRepository orderRepository;
    private final StaleOrderExpirer expirer;

    /**
     * 카드·간편결제 주문을 접기까지의 시간(분). 운영에서 바꾸려면 api.env 로 조절한다.
     *
     * <p><b>무통장입금에는 쓰이지 않는다.</b> 무통장은 주문마다 안내한 입금 기한
     * ({@code deposit_due_at})을 보고 접는다 — OrderRepository.findStale 주석 참조.
     */
    @Value("${app.order.stale-minutes:30}")
    private int staleMinutes;

    /**
     * 주기적으로 훑는다.
     *
     * <p>{@code fixedDelay} 다 — 앞 실행이 끝난 뒤부터 센다. {@code fixedRate} 로 두면 한 번이
     * 느려졌을 때 다음 실행이 겹쳐 같은 주문을 두 번 건드린다.
     */
    @Scheduled(fixedDelayString = "${app.order.sweep-interval-ms:300000}", initialDelay = 60_000)
    public void sweep() {
        OffsetDateTime now = OffsetDateTime.now();
        // 카드는 주문 시각 + 고정 시간, 무통장은 그 주문에 안내한 입금 기한을 본다.
        // 기준이 둘로 갈리는 이유는 OrderRepository.findStale 주석 참조.
        OffsetDateTime cutoff = now.minusMinutes(staleMinutes);
        List<Order> stale = orderRepository.findStale(
                List.of(OrderStatus.ORDER_CREATED, OrderStatus.PAYMENT_PENDING),
                cutoff, now, Limit.of(BATCH));

        if (stale.isEmpty()) {
            return;
        }

        int done = 0;
        for (Order order : stale) {
            // 한 건이 실패해도 나머지는 접는다. 한 트랜잭션으로 묶으면 하나 때문에 전부 남는다.
            try {
                if (expirer.expire(order.getId())) {
                    done++;
                }
            } catch (RuntimeException e) {
                log.warn("[ORDER] 방치 주문을 접지 못했습니다. orderNo={}", order.getOrderNo(), e);
            }
        }

        if (done > 0) {
            // 분 수를 적지 않는다 — 무통장은 이 값으로 접힌 것이 아니라 주문마다의
            // 입금 기한으로 접힌 것이라, 한 줄에 섞어 적으면 로그가 거짓말을 한다.
            log.info("[ORDER] 방치 주문 {}건을 접고 재고를 돌려주었습니다", done);
        }
    }
}
