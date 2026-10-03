package com.hanseongge.domain.order.service;

import com.hanseongge.domain.cart.entity.Cart;
import com.hanseongge.domain.cart.entity.CartItem;
import com.hanseongge.domain.cart.repository.CartItemRepository;
import com.hanseongge.domain.cart.repository.CartRepository;
import com.hanseongge.domain.order.dto.OrderCreateRequest;
import com.hanseongge.domain.order.dto.OrderResponse;
import com.hanseongge.domain.order.entity.Order;
import com.hanseongge.domain.order.entity.OrderItem;
import com.hanseongge.domain.order.entity.OrderItemComponent;
import com.hanseongge.domain.order.repository.OrderItemComponentRepository;
import com.hanseongge.domain.order.repository.OrderItemRepository;
import com.hanseongge.domain.order.repository.OrderRepository;
import com.hanseongge.domain.pricing.service.PriceCalculationService;
import com.hanseongge.domain.pricing.service.PriceQuote;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductOption;
import com.hanseongge.domain.product.repository.ProductOptionRepository;
import com.hanseongge.domain.product.repository.ProductRepository;
import com.hanseongge.domain.setting.service.SystemSettingService;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 주문 (STEP 13).
 *
 * <h2>이 클래스가 지키는 것</h2>
 * <ol>
 *   <li><b>금액은 여기서만 정해진다.</b> 화면이 보낸 금액을 받는 경로가 없다 (원칙 P1)</li>
 *   <li><b>주문 시점에 다시 계산한다.</b> 장바구니에 담을 때 값이 아니라, 주문서를 만드는
 *       그 순간의 시세로 계산해 스냅샷으로 박는다</li>
 *   <li><b>재고를 주문서 생성에서 가져간다.</b> 결제 승인에서 빼면 마지막 하나를 두 사람이
 *       모두 결제하는 일이 생긴다</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final DateTimeFormatter ORDER_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String ORDER_NO_PREFIX = "HS";

    /** 주문번호 충돌 시 재시도 횟수. 같은 날 같은 순번을 두 요청이 동시에 집을 때를 위한 것이다. */
    private static final int ORDER_NO_RETRY = 5;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemComponentRepository componentRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository optionRepository;
    private final PriceCalculationService priceCalculationService;
    private final SystemSettingService settingService;

    // =================================================================
    // 생성
    // =================================================================

    /**
     * 장바구니를 주문서로 만든다.
     *
     * <p>담긴 줄 중 <b>하나라도</b> 주문할 수 없으면 주문서를 만들지 않는다. 문제가 있는 줄만
     * 빼고 진행하면, 고객은 자기가 고른 것이 다 들어간 줄 알고 결제한다 — 결제가 끝난 뒤에야
     * 빠진 것을 안다.
     */
    @Transactional
    public OrderResponse createFromCart(Long userId, OrderCreateRequest request) {
        List<CartItem> cartItems = cartItemsOf(userId);
        if (cartItems.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_NOTHING_TO_ORDER);
        }

        BigDecimal itemsTotal = BigDecimal.ZERO;
        List<Priced> priced = new ArrayList<>(cartItems.size());
        for (CartItem cartItem : cartItems) {
            Priced line = price(cartItem);
            priced.add(line);
            itemsTotal = itemsTotal.add(line.quote().totalFor(line.quantity()).value());
        }

        BigDecimal shippingFee = shippingFeeFor(itemsTotal);
        Order order = saveWithOrderNo(userId, itemsTotal, shippingFee, request);

        List<OrderItem> items = new ArrayList<>(priced.size());
        for (Priced line : priced) {
            // 재고는 여기서 가져간다. 모자라면 트랜잭션이 통째로 돌아가 주문도 남지 않는다.
            line.option().decreaseStock(line.quantity());

            OrderItem item = orderItemRepository.save(
                    OrderItem.snapshot(order.getId(), line.product(), line.option(),
                            line.quote(), line.quantity()));
            items.add(item);

            // 금액이 어떻게 나왔는지를 함께 남긴다 (V8 D-2). 나중에 다시 계산할 수 없다.
            componentRepository.saveAll(line.quote().components().stream()
                    .map(component -> OrderItemComponent.snapshot(item.getId(), component))
                    .toList());
        }

        // ⚠️ 여기서 장바구니를 비우지 않는다.
        //
        // 주문서는 아직 결제되지 않은 상태다. 여기서 비우면 결제창에서 그만둔 고객이
        // <b>돌아왔을 때 빈 장바구니를 본다</b> — 무엇을 담았는지 다시 찾아야 하고,
        // 그 지점에서 대부분 떠난다. 비우는 일은 결제가 승인된 뒤에 한다
        // ({@code PaymentService.settle}).

        log.info("[ORDER] 주문서 생성 orderNo={} userId={} amount={} lines={}",
                order.getOrderNo(), userId, order.getTotalAmount(), items.size());
        return OrderResponse.of(order, items);
    }

    /**
     * 결제가 끝난 주문에 들어간 줄만 장바구니에서 지운다.
     *
     * <p>통째로 비우지 않는 이유 — 주문서를 만든 뒤 결제하기까지 사이에 다른 상품을
     * 담았을 수 있다. 그것까지 지우면 산 적도 없는 물건이 사라진다.
     */
    @Transactional
    public void clearOrdered(Long userId, List<OrderItem> items) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null || items.isEmpty()) {
            return;
        }
        for (OrderItem item : items) {
            cartItemRepository
                    .findByCartIdAndProductIdAndOptionId(cart.getId(), item.getProductId(), item.getOptionId())
                    .ifPresent(cartItemRepository::delete);
        }
    }

    /** 장바구니 한 줄을 주문 가능 여부까지 확인하며 값매김한다. */
    private Priced price(CartItem cartItem) {
        Product product = productRepository.findById(cartItem.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_ITEM_UNAVAILABLE));
        ProductOption option = optionRepository.findById(cartItem.getOptionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_ITEM_UNAVAILABLE));

        PriceQuote quote = priceCalculationService.quote(product.getId(), option.getId());
        if (!quote.status().isOrderable() || quote.finalUnitPrice() == null) {
            // 시세문의 상태의 상품은 금액을 정할 수 없다. 0 원으로 내보내면 안 된다.
            throw new BusinessException(ErrorCode.ORDER_ITEM_UNAVAILABLE);
        }
        if (option.getStock() < cartItem.getQuantity()) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }
        return new Priced(product, option, quote, cartItem.getQuantity());
    }

    private record Priced(Product product, ProductOption option, PriceQuote quote, int quantity) {
    }

    // =================================================================
    // 조회
    // =================================================================

    @Transactional(readOnly = true)
    public Page<OrderResponse> listMine(Long userId, Pageable pageable) {
        Page<Order> orders = orderRepository.findByUserIdOrderByOrderedAtDescIdDesc(userId, pageable);
        if (orders.isEmpty()) {
            return orders.map(order -> OrderResponse.of(order, List.of()));
        }

        // 주문마다 항목을 따로 조회하면 목록 한 장에 쿼리가 스무 번 나간다.
        Map<Long, List<OrderItem>> byOrder = orderItemRepository
                .findByOrderIdInOrderByIdAsc(orders.getContent().stream().map(Order::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        return orders.map(order -> OrderResponse.of(order,
                byOrder.getOrDefault(order.getId(), List.of())));
    }

    @Transactional(readOnly = true)
    public OrderResponse getMine(Long userId, String orderNo) {
        Order order = requireOrder(userId, orderNo);
        return OrderResponse.of(order, orderItemRepository.findByOrderIdOrderByIdAsc(order.getId()));
    }

    /** 결제가 함께 쓰는 조회. 남의 주문이면 404 다. */
    @Transactional(readOnly = true)
    public Order requireOrder(Long userId, String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        order.requireOwnedBy(userId);
        return order;
    }

    // =================================================================
    // 취소
    // =================================================================

    /** 결제 전 주문을 고객이 스스로 취소한다. 가져갔던 재고를 돌려준다. */
    @Transactional
    public OrderResponse cancel(Long userId, String orderNo) {
        Order order = requireOrder(userId, orderNo);
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());

        order.cancel();
        restoreStock(items);

        log.info("[ORDER] 주문 취소 orderNo={} userId={}", orderNo, userId);
        return OrderResponse.of(order, items);
    }

    /**
     * 가져갔던 재고를 돌려준다. 결제 실패에서도 쓴다.
     *
     * <p>옵션이 사라졌으면 건너뛰고 로그만 남긴다 — 돌려줄 곳이 없다고 취소 자체가 실패하면
     * 고객은 취소도 못 하는 주문을 갖게 된다. 그 경우 재고는 사람이 맞춘다.
     */
    @Transactional
    public void restoreStock(List<OrderItem> items) {
        for (OrderItem item : items) {
            if (item.getOptionId() == null) {
                log.warn("[ORDER] 옵션 id 가 없어 재고를 돌려주지 못했습니다. orderItemId={}", item.getId());
                continue;
            }
            optionRepository.findById(item.getOptionId()).ifPresentOrElse(
                    option -> option.restoreStock(item.getQuantity()),
                    () -> log.warn("[ORDER] 재고를 돌려줄 옵션이 없습니다. orderItemId={} optionId={}",
                            item.getId(), item.getOptionId()));
        }
    }

    @Transactional(readOnly = true)
    public List<OrderItem> itemsOf(Long orderId) {
        return orderItemRepository.findByOrderIdOrderByIdAsc(orderId);
    }

    // =================================================================
    // 내부
    // =================================================================

    private List<CartItem> cartItemsOf(Long userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        return cart == null ? List.of()
                : cartItemRepository.findByCartIdOrderByAddedAtAscIdAsc(cart.getId());
    }

    /**
     * 배송비. {@code FREE_SHIPPING_THRESHOLD} 이상이면 0.
     *
     * <p>기준이 0 이면 "항상 무료"다 (V1 주석). 0 을 "기준 없음"으로 읽어 유료로 처리하면
     * 지금 설정(둘 다 0)에서 배송비가 붙는다.
     */
    private BigDecimal shippingFeeFor(BigDecimal itemsTotal) {
        BigDecimal fee = settingService.getDecimal("SHIPPING_FEE", BigDecimal.ZERO);
        if (fee.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal threshold = settingService.getDecimal("FREE_SHIPPING_THRESHOLD", BigDecimal.ZERO);
        if (threshold.signum() > 0 && itemsTotal.compareTo(threshold) >= 0) {
            return BigDecimal.ZERO;
        }
        return fee;
    }

    /**
     * 주문번호를 붙여 저장한다 — {@code HS20260928-000123}.
     *
     * <p>PG 에도 이 번호가 올라간다. 사람이 전화로 읽을 수 있어야 해서 UUID 를 쓰지 않는다.
     * 대신 순번이라 동시 요청이 같은 번호를 집을 수 있어, 유니크 위반이면 다시 센다.
     */
    private Order saveWithOrderNo(Long userId, BigDecimal itemsTotal, BigDecimal shippingFee,
                                  OrderCreateRequest request) {
        String prefix = ORDER_NO_PREFIX + LocalDate.now().format(ORDER_NO_DATE) + "-";
        DataIntegrityViolationException last = null;

        for (int attempt = 0; attempt < ORDER_NO_RETRY; attempt++) {
            long todayCount = orderRepository.countByOrderNoStartingWith(prefix);
            String orderNo = prefix + String.format("%06d", todayCount + 1 + attempt);
            try {
                return orderRepository.saveAndFlush(Order.create(orderNo, userId,
                        itemsTotal, shippingFee,
                        request.receiverName().trim(), request.receiverPhone().trim(),
                        request.zipcode().trim(), request.address1().trim(),
                        blankToNull(request.address2()), blankToNull(request.orderMemo())));
            } catch (DataIntegrityViolationException e) {
                last = e;
                log.warn("[ORDER] 주문번호 충돌 {}. 다시 채번한다 ({}회차)", orderNo, attempt + 1);
            }
        }
        throw last;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
