package com.hanseongge.domain.order.service;

import com.hanseongge.domain.order.entity.Order;
import com.hanseongge.domain.order.entity.OrderItem;
import com.hanseongge.domain.order.repository.OrderItemRepository;
import com.hanseongge.domain.order.repository.OrderRepository;
import com.hanseongge.domain.product.repository.ProductOptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 방치된 주문 <b>한 건</b>을 접는다. {@link StaleOrderSweeper} 가 건마다 부른다.
 *
 * <h2>왜 클래스를 나눴나</h2>
 * 처음에는 {@code StaleOrderSweeper} 안에 두었는데, <b>같은 클래스 안에서 부른
 * {@code @Transactional} 은 프록시를 타지 않아 트랜잭션 없이 돈다.</b> 그러면 엔티티가
 * 준영속 상태라 바뀐 값이 저장되지 않는다 — 로그에는 "접었다"고 찍히는데 DB 는 그대로다.
 * 로컬에서 실제로 그 일이 났고(2026-09-29), 그래서 다른 빈으로 뺐다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StaleOrderExpirer {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductOptionRepository optionRepository;

    /**
     * 한 건을 접고 재고를 돌려준다.
     *
     * <p>새 트랜잭션으로 연다 — 한 건이 실패해도 앞서 접은 것들이 되돌아가지 않아야 한다.
     *
     * <p>다시 읽어 상태를 확인하는 이유: 훑은 뒤 결제가 끝났을 수 있다. 그 주문을 접으면
     * <b>이미 받은 돈의 주문을 취소</b>하게 된다.
     *
     * @return 실제로 접었으면 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expire(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || !order.getStatus().isCancellableByCustomer()) {
            return false;
        }

        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(orderId);
        order.cancel();

        for (OrderItem item : items) {
            if (item.getOptionId() == null) {
                log.warn("[ORDER] 옵션 id 가 없어 재고를 돌려주지 못했습니다. orderItemId={}", item.getId());
                continue;
            }
            optionRepository.findById(item.getOptionId())
                    .ifPresent(option -> option.restoreStock(item.getQuantity()));
        }

        log.info("[ORDER] 방치 주문 자동 취소 orderNo={} 항목={}", order.getOrderNo(), items.size());
        return true;
    }
}
