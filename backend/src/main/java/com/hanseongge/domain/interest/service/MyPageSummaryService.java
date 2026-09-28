package com.hanseongge.domain.interest.service;

import com.hanseongge.domain.interest.dto.MyPageSummaryResponse;
import com.hanseongge.domain.order.entity.OrderStatus;
import com.hanseongge.domain.order.repository.OrderRepository;
import com.hanseongge.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 마이페이지 맨 위 요약.
 *
 * <p>숫자를 화면에서 세지 않는다. 주문 목록은 한 장씩 끊어 오므로 화면이 센 값은
 * <b>그 한 장의 개수</b>일 뿐이다 — "총 6건"이 "총 10건"으로 보이게 된다.
 */
@Service
@RequiredArgsConstructor
public class MyPageSummaryService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final InterestService interestService;

    @Transactional(readOnly = true)
    public MyPageSummaryResponse of(Long userId) {
        return new MyPageSummaryResponse(
                userRepository.findById(userId).map(u -> u.getName()).orElse(null),
                orderRepository.countByUserId(userId),
                count(userId, OrderStatus.ORDER_CREATED, OrderStatus.PAYMENT_PENDING),
                count(userId, OrderStatus.PAID, OrderStatus.PREPARING),
                count(userId, OrderStatus.SHIPPED),
                count(userId, OrderStatus.DELIVERED),
                count(userId, OrderStatus.CANCEL_REQUESTED, OrderStatus.CANCELLED,
                        OrderStatus.REFUND_PENDING, OrderStatus.REFUNDED),
                interestService.wishlistCount(userId));
    }

    private long count(Long userId, OrderStatus... statuses) {
        return orderRepository.countByUserIdAndStatusIn(userId, List.of(statuses));
    }
}
