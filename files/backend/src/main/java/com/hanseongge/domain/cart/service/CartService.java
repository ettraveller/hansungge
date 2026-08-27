package com.hanseongge.domain.cart.service;

import com.hanseongge.domain.cart.dto.CartItemResponse;
import com.hanseongge.domain.cart.dto.CartResponse;
import com.hanseongge.domain.cart.entity.Cart;
import com.hanseongge.domain.cart.entity.CartItem;
import com.hanseongge.domain.cart.repository.CartItemRepository;
import com.hanseongge.domain.cart.repository.CartRepository;
import com.hanseongge.domain.pricing.service.PriceCalculationService;
import com.hanseongge.domain.pricing.service.PriceQuote;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductOption;
import com.hanseongge.domain.product.entity.ProductStatus;
import com.hanseongge.domain.product.repository.ProductOptionRepository;
import com.hanseongge.domain.product.repository.ProductRepository;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 장바구니 (개발계획 §13.6).
 *
 * <h2>담을 때와 볼 때 모두 서버가 계산한다</h2>
 * 프론트엔드가 보내는 것은 <b>상품·옵션·수량</b>뿐이다. 금액은 담는 순간 서버가 계산해
 * {@code price_at_add} 에 적고, 조회할 때마다 현재 시세로 <b>다시</b> 계산해 그 값과
 * 견준다. 화면은 두 값을 받아 표시만 한다 (원칙 P1).
 *
 * <h2>담을 수 없는 경우를 담기 시점에 막는다</h2>
 * 시세를 확인할 수 없거나(시세문의) 판매 중이 아니거나 재고가 없으면 담기 자체를
 * 거절한다 (설계서 §12.4). 담아두고 결제 단계에서 막으면 사용자는 그때까지 들인
 * 시간을 잃는다.
 *
 * <p>다만 <b>담긴 뒤에 상황이 바뀌는 것은 막을 수 없다.</b> 시세가 노후되거나 재고가
 * 빠질 수 있다. 그래서 조회 시점에도 같은 검사를 하고, 줄마다 주문 가능 여부를 실어
 * 보낸다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    /**
     * 장바구니에 담을 수 있는 줄 수 상한.
     *
     * <p>없으면 자동화된 요청으로 수만 줄을 만들 수 있고, 조회할 때마다 그만큼 가격을
     * 계산하게 된다 — 장바구니 하나가 서버를 흔든다.
     */
    private static final int MAX_LINES = 50;

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository optionRepository;
    private final PriceCalculationService priceCalculationService;

    // =================================================================
    // 조회
    // =================================================================

    /**
     * 장바구니 조회 — <b>매번 다시 계산한다.</b>
     *
     * <p>저장된 합계를 돌려주면 시세가 바뀐 뒤에도 옛 금액을 보여주게 된다. 이 시스템에서
     * 상품 가격은 저장되는 값이 아니라 조회 시점에 만들어지는 값이다.
     */
    @Transactional(readOnly = true)
    public CartResponse view(Long userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            return CartResponse.empty();
        }

        List<CartItem> items = cartItemRepository.findByCartIdOrderByAddedAtAscIdAsc(cart.getId());
        if (items.isEmpty()) {
            return CartResponse.empty();
        }

        List<CartItemResponse> lines = new ArrayList<>(items.size());
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItem item : items) {
            Product product = productRepository.findById(item.getProductId()).orElse(null);
            ProductOption option = optionRepository.findById(item.getOptionId()).orElse(null);
            if (product == null || option == null) {
                // 상품이나 옵션이 사라진 줄. 조회가 통째로 실패하면 나머지 줄까지 못 본다.
                log.warn("[CART] 사라진 상품/옵션이 담겨 있습니다. cartItemId={} productId={} optionId={}",
                        item.getId(), item.getProductId(), item.getOptionId());
                continue;
            }

            PriceQuote quote = priceCalculationService.quote(product.getId(), option.getId());
            CartItemResponse line = CartItemResponse.of(item, product, option, quote, option.getStock());
            lines.add(line);

            // 합계에는 지금 주문할 수 있는 줄만 넣는다.
            if (line.orderable() && line.lineAmount() != null) {
                subtotal = subtotal.add(line.lineAmount());
            }
        }

        return CartResponse.of(lines, subtotal);
    }

    /** 헤더 배지용. 줄 수만 필요할 때 가격 계산까지 돌리지 않는다. */
    @Transactional(readOnly = true)
    public int count(Long userId) {
        return cartRepository.findByUserId(userId)
                .map(cart -> (int) cartItemRepository.countByCartId(cart.getId()))
                .orElse(0);
    }

    // =================================================================
    // 담기
    // =================================================================

    @Transactional
    public CartResponse add(Long userId, Long productId, Long optionId, int quantity) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND,
                        "상품 없음: " + productId));
        requireOnSale(product);

        ProductOption option = resolveOption(product, optionId);
        PriceQuote quote = priceCalculationService.quote(product.getId(), option.getId());
        requirePurchasable(quote);

        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(Cart.createFor(userId)));

        CartItem existing = cartItemRepository
                .findByCartIdAndProductIdAndOptionId(cart.getId(), product.getId(), option.getId())
                .orElse(null);

        int resulting = (existing == null ? 0 : existing.getQuantity()) + quantity;
        requireStock(option, resulting);
        int maxAllowed = maxQuantityOf(product);

        if (existing == null) {
            if (cartItemRepository.countByCartId(cart.getId()) >= MAX_LINES) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "장바구니에는 " + MAX_LINES + "종류까지 담을 수 있습니다.");
            }
            if (quantity > maxAllowed) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "이 상품은 최대 " + maxAllowed + "개까지 담을 수 있습니다.");
            }
            cartItemRepository.save(CartItem.of(cart.getId(), product.getId(), option.getId(),
                    quantity, quote.finalUnitPrice().value(), quote.effectivePriceId()));
        } else {
            existing.addQuantity(quantity, maxAllowed,
                    quote.finalUnitPrice().value(), quote.effectivePriceId());
        }

        cart.touch();
        log.info("[CART] 담기 userId={} productId={} optionId={} qty={}",
                userId, product.getId(), option.getId(), quantity);
        return view(userId);
    }

    // =================================================================
    // 수정 · 삭제
    // =================================================================

    @Transactional
    public CartResponse changeQuantity(Long userId, Long itemId, int quantity) {
        Cart cart = requireCart(userId);
        CartItem item = requireItem(cart, itemId);

        Product product = productRepository.findById(item.getProductId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        ProductOption option = optionRepository.findById(item.getOptionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "옵션 없음"));

        requireStock(option, quantity);
        item.changeQuantity(quantity, maxQuantityOf(product));
        cart.touch();
        return view(userId);
    }

    @Transactional
    public CartResponse remove(Long userId, Long itemId) {
        Cart cart = requireCart(userId);
        cartItemRepository.delete(requireItem(cart, itemId));
        cart.touch();
        return view(userId);
    }

    @Transactional
    public CartResponse clear(Long userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            return CartResponse.empty();
        }
        int removed = cartItemRepository.deleteAllByCartId(cart.getId());
        cart.touch();
        log.info("[CART] 비우기 userId={} 삭제 {}건", userId, removed);
        return CartResponse.empty();
    }

    // =================================================================
    // 검사
    // =================================================================

    private void requireOnSale(Product product) {
        if (!product.isActive() || product.getStatus() != ProductStatus.ON_SALE) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE,
                    "판매중이 아닌 상품: " + product.getId() + " status=" + product.getStatus());
        }
    }

    /**
     * 지금 담을 수 있는 가격인지.
     *
     * <p>시세를 확인할 수 없으면 금액이 {@code null} 이다. 그 상태로 담으면
     * {@code price_at_add} 에 넣을 값이 없고, 0 을 넣는 순간 "0원짜리 장바구니 줄"이
     * 만들어진다 — 이 시스템이 가장 피해야 할 상태다 (원칙 P6).
     */
    private void requirePurchasable(PriceQuote quote) {
        if (!quote.status().isOrderable() || quote.finalUnitPrice() == null) {
            throw new BusinessException(ErrorCode.PRICE_UNAVAILABLE,
                    "담을 수 없는 가격 상태: " + quote.status() + " " + quote.unavailableReason());
        }
    }

    private void requireStock(ProductOption option, int wanted) {
        if (option.getStock() < wanted) {
            throw new BusinessException(ErrorCode.OUT_OF_STOCK,
                    "재고 부족: 재고 " + option.getStock() + " < 요청 " + wanted);
        }
    }

    /**
     * 옵션을 정한다. 지정하지 않았으면 기본 옵션을 쓴다.
     *
     * <p>다른 상품의 옵션 ID 를 넣는 요청을 막는다 — 막지 않으면 A 상품 가격으로
     * B 상품 옵션 재고를 소모시킬 수 있다.
     */
    private ProductOption resolveOption(Product product, Long optionId) {
        List<ProductOption> options = optionRepository
                .findByProductIdAndActiveTrueOrderBySortAscIdAsc(product.getId());
        if (options.isEmpty()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE,
                    "판매 가능한 옵션이 없음: " + product.getId());
        }
        if (optionId == null) {
            return options.get(0);
        }
        return options.stream()
                .filter(o -> o.getId().equals(optionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "이 상품의 옵션이 아닙니다: " + optionId));
    }

    /** 상품별 상한이 있으면 그쪽이 우선한다. */
    private int maxQuantityOf(Product product) {
        Integer max = product.getMaxQty();
        if (max == null || max > CartItem.MAX_QUANTITY) {
            return CartItem.MAX_QUANTITY;
        }
        return max;
    }

    private Cart requireCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "장바구니 없음"));
    }

    /** 남의 장바구니 줄을 건드리지 못하게 cart 소유까지 함께 확인한다. */
    private CartItem requireItem(Cart cart, Long itemId) {
        return cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "장바구니 항목 없음: " + itemId));
    }
}
