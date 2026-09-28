package com.hanseongge.domain.interest.service;

import com.hanseongge.domain.interest.entity.RecentlyViewed;
import com.hanseongge.domain.interest.entity.WishlistItem;
import com.hanseongge.domain.interest.repository.RecentlyViewedRepository;
import com.hanseongge.domain.interest.repository.WishlistItemRepository;
import com.hanseongge.domain.pricing.service.PriceCalculationService;
import com.hanseongge.domain.pricing.service.PriceQuote;
import com.hanseongge.domain.product.dto.ProductSummaryResponse;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.repository.ProductOptionRepository;
import com.hanseongge.domain.product.repository.ProductRepository;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 찜한 상품 · 최근 본 상품 (2026-09-28).
 *
 * <h2>금액을 저장하지 않는다</h2>
 * 둘 다 상품 id 만 담는다. 이 쇼핑몰의 가격은 시세로 <b>매번 만들어지는 값</b>이라,
 * 찜한 시점의 금액을 저장하면 다음 날이면 틀린 값이 된다. 목록을 열 때 다시 계산한다.
 *
 * <h2>주문과 반대로 FK 를 건다</h2>
 * 주문은 "산 사실"이라 상품이 지워져도 남아야 하지만, 이 둘은 "지금 관심 있는 것"이라
 * 상품이 사라지면 같이 사라지는 게 맞다 (V26). 없어진 상품이 찜 목록에 남아 있으면
 * 고객은 눌렀을 때 404 만 만난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterestService {

    /**
     * 최근 본 상품 보관 개수.
     *
     * <p>상한이 없으면 이 표가 회원 한 명당 끝없이 자란다. 화면에서 스무 개 넘게 훑는
     * 사람은 거의 없으므로 넉넉히 50 이면 충분하다.
     */
    private static final int RECENT_LIMIT = 50;

    private final WishlistItemRepository wishlistRepository;
    private final RecentlyViewedRepository recentRepository;
    private final ProductRepository productRepository;
    private final ProductOptionRepository optionRepository;
    private final PriceCalculationService priceCalculationService;

    // =================================================================
    // 찜한 상품
    // =================================================================

    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> wishlist(Long userId) {
        return summarize(wishlistRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(WishlistItem::getProductId)
                .toList());
    }

    /**
     * 찜하기. 이미 찜했으면 아무 일도 하지 않는다.
     *
     * <p>두 번 눌렀다고 오류를 내지 않는다 — 하트는 연타되는 버튼이고, 고객이 원하는
     * 결과("찜한 상태")는 이미 이루어져 있다.
     */
    @Transactional
    public void addToWishlist(Long userId, Long productId) {
        requireProduct(productId);
        if (wishlistRepository.existsByUserIdAndProductId(userId, productId)) {
            return;
        }
        try {
            wishlistRepository.save(WishlistItem.of(userId, productId));
        } catch (DataIntegrityViolationException e) {
            // 같은 사람이 동시에 두 번 눌렀다. 유니크 제약이 막아 준 것이고 결과는 같다.
            log.debug("[INTEREST] 찜 중복 요청 userId={} productId={}", userId, productId);
        }
    }

    @Transactional
    public void removeFromWishlist(Long userId, Long productId) {
        wishlistRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Transactional(readOnly = true)
    public boolean isWishlisted(Long userId, Long productId) {
        return wishlistRepository.existsByUserIdAndProductId(userId, productId);
    }

    /** 목록 화면용 — 보고 있는 상품들의 찜 여부를 한 번에. 상품마다 묻지 않는다. */
    @Transactional(readOnly = true)
    public Set<Long> wishlistedAmong(Long userId, List<Long> productIds) {
        if (userId == null || productIds.isEmpty()) {
            return Set.of();
        }
        return wishlistRepository.findByUserIdAndProductIdIn(userId, productIds).stream()
                .map(WishlistItem::getProductId)
                .collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public long wishlistCount(Long userId) {
        return wishlistRepository.countByUserId(userId);
    }

    // =================================================================
    // 최근 본 상품
    // =================================================================

    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> recentlyViewed(Long userId, int size) {
        return summarize(recentRepository
                .findByUserIdOrderByViewedAtDescIdDesc(userId, PageRequest.of(0, Math.min(size, RECENT_LIMIT)))
                .stream()
                .map(RecentlyViewed::getProductId)
                .toList());
    }

    /**
     * 본 것으로 기록한다.
     *
     * <p>상품 상세를 열 때마다 불린다. <b>실패해도 상세 화면이 망가지면 안 되므로</b>
     * 부르는 쪽에서 예외를 삼킨다 (컨트롤러 참조). 여기서는 조용히 최신화만 한다.
     */
    @Transactional
    public void markViewed(Long userId, Long productId) {
        requireProduct(productId);

        recentRepository.findByUserIdAndProductId(userId, productId)
                .ifPresentOrElse(RecentlyViewed::touch, () -> {
                    try {
                        recentRepository.save(RecentlyViewed.of(userId, productId));
                    } catch (DataIntegrityViolationException e) {
                        log.debug("[INTEREST] 최근 본 상품 중복 userId={} productId={}", userId, productId);
                    }
                });

        trimRecent(userId);
    }

    @Transactional
    public void clearRecentlyViewed(Long userId) {
        recentRepository.deleteByUserId(userId);
    }

    /** 상한을 넘으면 오래된 것부터 지운다. */
    private void trimRecent(Long userId) {
        long count = recentRepository.countByUserId(userId);
        if (count <= RECENT_LIMIT) {
            return;
        }
        int excess = (int) (count - RECENT_LIMIT);
        recentRepository.deleteAll(
                recentRepository.findByUserIdOrderByViewedAtAscIdAsc(userId, PageRequest.of(0, excess)));
    }

    // =================================================================
    // 내부
    // =================================================================

    /**
     * 상품 id 목록을 <b>넘어온 순서 그대로</b> 요약으로 바꾼다.
     *
     * <p>순서가 뒤집히면 "최근 본 상품"이 최근이 아니게 된다. 그래서 DB 의 IN 조회 결과를
     * 그대로 쓰지 않고 원래 순서에 맞춰 다시 담는다.
     */
    private List<ProductSummaryResponse> summarize(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        var byId = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        List<ProductSummaryResponse> out = new ArrayList<>(productIds.size());
        for (Long id : productIds) {
            Product product = byId.get(id);
            if (product == null) {
                // FK 가 걸려 있어 보통은 없지만, 방어적으로 건너뛴다.
                continue;
            }
            PriceQuote quote = priceCalculationService.quote(product.getId(), null);
            boolean multipleOptions = optionRepository.countByProductIdAndActiveTrue(product.getId()) > 1;
            out.add(ProductSummaryResponse.of(product, quote, multipleOptions));
        }
        return out;
    }

    private void requireProduct(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
    }
}
