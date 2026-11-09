package com.hanseongge.domain.product.service;

import com.hanseongge.domain.product.dto.ProductSearchCondition;
import com.hanseongge.domain.product.entity.DeliveryNotice;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.entity.ProductImage;
import com.hanseongge.domain.pricing.service.PriceCalculationService;
import com.hanseongge.domain.pricing.service.PriceQuote;
import com.hanseongge.domain.product.dto.ProductDetailResponse;
import com.hanseongge.domain.product.dto.ProductDraftDetailRequest;
import com.hanseongge.domain.product.dto.ProductDraftPriceRequest;
import com.hanseongge.domain.product.dto.ProductOptionResponse;
import com.hanseongge.domain.pricing.dto.PublicPriceQuoteResponse;
import com.hanseongge.domain.product.entity.ProductImageKind;
import com.hanseongge.domain.product.entity.ProductOption;
import com.hanseongge.domain.product.entity.ProductPricing;
import com.hanseongge.domain.product.entity.ProductStatus;
import com.hanseongge.domain.product.repository.ProductImageRepository;
import com.hanseongge.domain.product.repository.ProductOptionRepository;
import com.hanseongge.domain.product.repository.ProductQuery;
import com.hanseongge.domain.product.repository.ProductRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.audit.AuditContext;
import com.hanseongge.global.audit.AuditLog;
import com.hanseongge.global.config.CacheConfig;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 상품 관리 (설계서 §8 상품관리, 개발계획 §9).
 *
 * <h2>재고는 항상 옵션에만 있다 (개발계획 §9.3, 결함 D-7)</h2>
 * 옵션 없이 상품을 등록하면 <b>서버가 기본 옵션 1개를 자동 생성</b>한다. "옵션 없는 상품"
 * 개념을 없애면
 * <ul>
 *   <li>재고 소스가 {@code product_options.stock} 한 곳뿐이다 (이중 재고 없음)</li>
 *   <li>장바구니·주문의 {@code option_id} 가 항상 채워진다</li>
 *   <li>재고 차감·복원이 분기 없이 단일 경로다 (STEP 11~13)</li>
 * </ul>
 * {@code products.stock} 컬럼을 추가하는 대안은 옵션 유무에 따라 재고 소스가 갈리는
 * 분기를 주문·취소·환불 전 경로에 만들기 때문에 채택하지 않았다.
 *
 * <p>다만 <b>{@code is_default} 플래그와 부분 UNIQUE 인덱스는 아직 없다</b>(V8 대기).
 * 지금은 "활성 옵션이 최소 1개"를 응용 계층이 지킨다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductOptionRepository optionRepository;
    private final ProductImageRepository imageRepository;
    private final CategoryService categoryService;
    /** 등록 전 미리보기에만 쓴다. 저장된 상품의 가격은 각 조회 경로가 직접 부른다. */
    private final PriceCalculationService priceCalculationService;
    private final CacheManager cacheManager;

    // =================================================================
    // 조회
    // =================================================================

    /**
     * 상품 검색.
     *
     * <p>요청 조건을 <b>여기서 해석</b>한다 — 카테고리를 하위까지 펼치고, 공개 조회면
     * 관리자 전용 조건(status·active)을 지운다. 저장소가 이 판단을 하기 시작하면
     * 같은 규칙이 두 곳에 생긴다.
     *
     * @param visibleOnly 공개 조회 여부. <b>호출부가 명시한다</b> — 요청 파라미터로 두면
     *                    클라이언트가 숨김 상품을 열람할 수 있다
     */
    @Transactional(readOnly = true)
    public Page<Product> search(ProductSearchCondition condition, boolean visibleOnly, Pageable pageable) {
        return productRepository.search(toQuery(condition, visibleOnly), pageable);
    }

    private ProductQuery toQuery(ProductSearchCondition condition, boolean visibleOnly) {
        if (condition == null) {
            return new ProductQuery(List.of(), null, null, null, null, null, visibleOnly);
        }
        List<Long> categoryIds = condition.categoryId() == null
                ? List.of()
                : categoryService.descendantIds(condition.categoryId());

        return new ProductQuery(
                categoryIds,
                condition.keyword(),
                condition.metalType(),
                condition.priceType(),
                visibleOnly ? null : condition.status(),
                visibleOnly ? null : condition.active(),
                visibleOnly);
    }

    @Transactional(readOnly = true)
    public Product find(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "상품 없음: " + id));
    }

    /** 공개 조회 — 숨김·판매종료·비활성 상품은 존재 자체를 알리지 않는다. */
    @Transactional(readOnly = true)
    public Product findVisible(Long id) {
        Product product = find(id);
        if (!product.isVisible()) {
            // 404 로 돌려준다. 403 이면 "숨긴 상품이 여기 있다"는 사실이 새어 나간다.
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "노출 불가 상품 조회: " + id);
        }
        return product;
    }

    @Transactional(readOnly = true)
    public List<ProductOption> findActiveOptions(Long productId) {
        return optionRepository.findByProductIdAndActiveTrueOrderBySortAscIdAsc(productId);
    }

    @Transactional(readOnly = true)
    public List<ProductOption> findAllOptions(Long productId) {
        return optionRepository.findByProductIdOrderBySortAscIdAsc(productId);
    }

    @Transactional(readOnly = true)
    public List<ProductImage> findImages(Long productId, ProductImageKind kind) {
        return imageRepository.findByProductIdAndKindOrderBySortAscIdAsc(productId, kind);
    }

    /**
     * 등록 전 상세 화면 미리보기 — 상품을 만들지 않고 입력값만으로 조립한다.
     *
     * <p>등록 직후 상품은 {@code HIDDEN} 이라 고객 화면으로 확인할 방법이 없다. 그것이
     * 없으면 가격을 보려고 "일단 판매중으로 바꿔 보고 되돌리는" 절차를 쓰게 되고, 그 몇 초
     * 동안 확인되지 않은 가격이 실제로 주문 가능한 상태가 된다.
     *
     * <p>응답이 고객 화면이 받는 것과 <b>같은 모양</b>이다({@link ProductDetailResponse}).
     * 관리자 화면은 그 응답으로 고객용 상세 컴포넌트를 그대로 그린다 — 비슷하게 다시
     * 그리면 쇼핑몰이 바뀔 때 미리보기만 낡아서 "미리보기와 실제가 다르다"가 된다.
     *
     * <p>금액도 화면에서 계산하지 않는다. 시세·보정·부가세·반올림 규칙이 전부 서버에
     * 있고, 둘이 갈라지는 날 미리보기와 실제 판매가가 달라진다 (설계서 원칙 P1).
     *
     * <p>⚠️ 여기서 만드는 {@link Product}·{@link ProductOption} 은 <b>리포지토리에 넘기지
     * 않는다.</b> 저장하면 폼을 채워 보기만 해도 상품이 생기고, 그 상품은 아무도 지우지
     * 않는다. 영속화되지 않은 인스턴스라 id 가 null 이다.
     *
     * <p>카테고리는 받지 않는다. 가격에도 화면에도 쓰이지 않는데 필수로 두면, 폼을 반만
     * 채운 상태에서 미리보기를 볼 수 없게 된다 — 확인하려는 시점이 바로 그 상태다.
     */
    @Transactional(readOnly = true)
    public ProductDetailResponse previewDraftDetail(ProductDraftDetailRequest request) {
        ProductDraftPriceRequest pricingRequest = request.pricing();
        ProductPricing pricing = pricingRequest.toPricing();

        Product draft = Product.create(null, request.name(), pricing, request.description(),
                request.galleryKeys().isEmpty() ? null : request.galleryKeys().get(0),
                request.madeToOrder(), request.vatTaxable(), request.toDeliveryNotice(),
                "preview");

        // ⚠️ 판매중으로 두고 계산한다. Product.create 는 HIDDEN 으로 시작하는데, 그대로
        // 두면 공개 응답이 금액을 가리고 "시세문의"만 내보낸다 — 판매하지 않는 상품의
        // 가격을 고객에게 보여주지 않기 위한 규칙이고, 그 자체는 맞다.
        //
        // 다만 미리보기에서 확인하려는 것이 바로 그 금액이다. 그래서 이 미리보기는
        // <b>"판매를 시작하면 이렇게 보인다"</b>를 그린다. 관리자 화면이 그 전제를
        // 화면에 적는다 (ProductPreviewModal).
        draft.changeStatus(ProductStatus.ON_SALE, "preview");

        ProductOption draftOption = draftOptionOf(pricingRequest);

        return ProductDetailResponse.of(draft,
                request.galleryKeys(), request.detailKeys(),
                List.of(ProductOptionResponse.forPublic(draftOption)),
                List.of(PublicPriceQuoteResponse.from(
                        priceCalculationService.previewDraft(draft, draftOption))));
    }

    /**
     * 저장하지 않는 기본 옵션.
     *
     * <p>등록하면 실제로 기본 옵션 하나가 만들어진다 (D-7). 재고를 1 로 두는 이유는
     * 0 이면 품절로 판정되어 금액 대신 품절 상태가 돌아오기 때문이다 — 미리보기에서
     * 보고 싶은 것은 금액이다.
     */
    private ProductOption draftOptionOf(ProductDraftPriceRequest request) {
        ProductPricing pricing = request.toPricing();
        return ProductOption.createDefault(
                null, resolveDefaultOptionName(null, pricing), pricing.weightG(),
                request.additionalLaborCost() == null ? BigDecimal.ZERO : request.additionalLaborCost(),
                1, 0);
    }

    // =================================================================
    // 등록 / 수정
    // =================================================================

    /**
     * 상품 등록. {@code HIDDEN} 상태로 시작한다.
     *
     * @param defaultOptionName 기본 옵션 이름. 비우면 중량으로 자동 생성한다
     */
    @Transactional
    @AuditLog(action = AuditActions.PRODUCT_CREATE,
              targetType = AuditActions.TARGET_PRODUCT,
              targetId = "#result.getId()")
    public Product create(Long categoryId, String name, ProductPricing pricing,
                          String description, boolean madeToOrder, boolean vatTaxable,
                          DeliveryNotice delivery, String defaultOptionName,
                          Integer initialStock, String adminLoginId) {

        categoryService.find(categoryId);   // 존재 확인

        // 썸네일은 null 로 시작한다. 새 상품에는 아직 대표이미지 목록이 없고,
        // 그 목록의 첫 장이 들어오는 순간 replaceImages(GALLERY) 가 채운다.
        Product product = productRepository.save(Product.create(
                categoryId, name, pricing, description, null, madeToOrder, vatTaxable,
                delivery, adminLoginId));

        // 재고를 담을 곳을 반드시 하나 만든다 (D-7)
        ProductOption defaultOption = optionRepository.save(ProductOption.createDefault(
                product.getId(),
                resolveDefaultOptionName(defaultOptionName, pricing),
                pricing.weightG(),
                BigDecimal.ZERO,
                initialStock == null ? 0 : initialStock,
                0));

        log.info("[PRODUCT] 등록 id={} name={} priceType={} 기본옵션id={}",
                product.getId(), name, pricing.priceType(), defaultOption.getId());
        return product;
    }

    @Transactional
    @AuditLog(action = AuditActions.PRODUCT_UPDATE,
              targetType = AuditActions.TARGET_PRODUCT,
              targetId = "#id")
    public Product update(Long id, Long categoryId, String name, ProductPricing pricing,
                          String description, boolean madeToOrder, boolean vatTaxable,
                          DeliveryNotice delivery, String adminLoginId) {
        Product product = find(id);
        AuditContext.before(snapshot(product));

        categoryService.find(categoryId);
        product.update(categoryId, name, pricing, description, madeToOrder, vatTaxable,
                delivery, adminLoginId);

        // 가격 재료가 바뀌었으므로 캐시된 산출 결과는 더 이상 유효하지 않다.
        evictPriceCache();
        return product;
    }

    /**
     * 판매 상태 변경.
     *
     * <p>{@code ON_SALE} 로 바꿀 때 <b>활성 옵션이 있는지</b> 확인한다. 옵션이 없으면
     * 재고가 존재할 곳이 없어 주문이 곧바로 실패하고, 고객은 "담기"를 누른 뒤에야 그 사실을 안다.
     */
    @Transactional
    @AuditLog(action = AuditActions.PRODUCT_STATUS_CHANGE,
              targetType = AuditActions.TARGET_PRODUCT,
              targetId = "#id",
              after = "{'status': #status.name(), 'active': #active, 'reason': #reason}")
    public Product changeStatus(Long id, ProductStatus status, boolean active,
                                String reason, String adminLoginId) {
        Product product = find(id);
        AuditContext.before(Map.of(
                "status", product.getStatus().name(),
                "active", product.isActive()));

        if (status == ProductStatus.ON_SALE && active
                && optionRepository.countByProductIdAndActiveTrue(id) == 0) {
            throw new BusinessException(ErrorCode.PRODUCT_LAST_OPTION,
                    "활성 옵션 없이 판매 시작 시도: productId=" + id);
        }

        product.changeStatus(status, adminLoginId);
        product.changeActive(active, adminLoginId);
        evictPriceCache();

        log.info("[PRODUCT] 상태 변경 id={} status={} active={} 사유={} by={}",
                id, status, active, reason, adminLoginId);
        return product;
    }

    // =================================================================
    // 옵션
    // =================================================================

    @Transactional
    @AuditLog(action = AuditActions.PRODUCT_OPTION_CREATE,
              targetType = AuditActions.TARGET_PRODUCT_OPTION,
              targetId = "#result.getId()")
    public ProductOption addOption(Long productId, String name, BigDecimal weightG,
                                   BigDecimal additionalLaborCost, int stock, int sort) {
        find(productId);   // 존재 확인
        ProductOption option = optionRepository.save(ProductOption.create(
                productId, name, weightG, additionalLaborCost, stock, sort));
        evictPriceCache();
        return option;
    }

    @Transactional
    @AuditLog(action = AuditActions.PRODUCT_OPTION_UPDATE,
              targetType = AuditActions.TARGET_PRODUCT_OPTION,
              targetId = "#optionId")
    public ProductOption updateOption(Long productId, Long optionId, String name, BigDecimal weightG,
                                      BigDecimal additionalLaborCost, int sort, boolean active) {
        ProductOption option = findOption(productId, optionId);
        AuditContext.before(optionSnapshot(option));

        // 마지막 활성 옵션을 끄면 재고가 존재할 곳이 사라진다 (D-7 불변식).
        if (option.isActive() && !active
                && optionRepository.countByProductIdAndActiveTrue(productId) <= 1) {
            throw new BusinessException(ErrorCode.PRODUCT_LAST_OPTION,
                    "마지막 활성 옵션 비활성화 시도: productId=" + productId);
        }

        option.update(name, weightG, additionalLaborCost, sort, active);
        evictPriceCache();
        return option;
    }

    /**
     * 재고 실사 반영.
     *
     * <p>주문에 의한 차감과 <b>다른 행위</b>다. 같은 메서드로 두면 실사 입력이 주문 차감과
     * 경합해 재고가 어긋나고, 감사로그에서도 둘을 구분할 수 없게 된다.
     */
    @Transactional
    @AuditLog(action = AuditActions.PRODUCT_STOCK_ADJUST,
              targetType = AuditActions.TARGET_PRODUCT_OPTION,
              targetId = "#optionId",
              after = "{'stock': #newStock, 'reason': #reason}")
    public ProductOption adjustStock(Long productId, Long optionId, int newStock,
                                     String reason, String adminLoginId) {
        ProductOption option = findOption(productId, optionId);
        AuditContext.before(Map.of("stock", option.getStock()));

        try {
            option.adjustStock(newStock);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
        }

        log.info("[PRODUCT] 재고 조정 optionId={} stock={} 사유={} by={}",
                optionId, newStock, reason, adminLoginId);
        return option;
    }

    // =================================================================
    // 이미지
    // =================================================================

    /**
     * 한 갈래의 이미지 목록 교체.
     *
     * <p>부분 수정 대신 통째로 갈아끼운다. 순서가 의미를 갖는 목록이라, 개별 추가·삭제를
     * 열어두면 "3번을 지우고 5번을 2번 자리로" 같은 요청이 여러 번에 나뉘어 중간 상태가
     * 화면에 노출된다.
     *
     * <p><b>다른 갈래는 건드리지 않는다.</b> 대표이미지를 바꿀 때 상세페이지가 함께
     * 날아가면 되돌릴 방법이 없다.
     *
     * <p>{@link ProductImageKind#GALLERY} 를 교체하면 {@code thumbnail_key} 도 첫 장으로
     * 맞춘다 — 목록·장바구니·주문서가 그 컬럼을 읽는다. 관리자가 두 곳을 따로 관리하면
     * 반드시 어긋나고, 어긋나면 목록의 사진과 상세의 첫 사진이 다른 상품이 된다.
     */
    @Transactional
    @AuditLog(action = AuditActions.PRODUCT_IMAGE_CHANGE,
              targetType = AuditActions.TARGET_PRODUCT,
              targetId = "#productId",
              after = "{'kind': #kind, 'objectKeys': #objectKeys}")
    public List<ProductImage> replaceImages(Long productId, ProductImageKind kind,
                                            List<String> objectKeys) {
        Product product = find(productId);

        List<ProductImage> existing =
                imageRepository.findByProductIdAndKindOrderBySortAscIdAsc(productId, kind);
        AuditContext.before(Map.of("kind", kind, "objectKeys",
                existing.stream().map(ProductImage::getObjectKey).toList()));
        imageRepository.deleteAll(existing);
        imageRepository.flush();   // 삭제를 먼저 내보낸다. 같은 키를 다시 넣는 경우가 흔하다

        List<ProductImage> saved = new java.util.ArrayList<>();
        for (int i = 0; i < objectKeys.size(); i++) {
            saved.add(imageRepository.save(
                    ProductImage.create(productId, objectKeys.get(i), i * 10, kind)));
        }

        if (kind == ProductImageKind.GALLERY) {
            // 비면 null 이다. 빈 문자열을 넣으면 "사진이 있다"로 읽혀서 화면이
            // 깨진 이미지 자리를 그린다.
            product.changeThumbnailKey(objectKeys.isEmpty() ? null : objectKeys.get(0));
        }
        return saved;
    }

    // =================================================================
    // 내부
    // =================================================================

    private ProductOption findOption(Long productId, Long optionId) {
        ProductOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND,
                        "옵션 없음: " + optionId));
        if (!option.getProductId().equals(productId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "옵션이 상품에 속하지 않습니다: productId=%d optionId=%d".formatted(productId, optionId));
        }
        return option;
    }

    /** 기본 옵션 이름. 중량이 있으면 "3.75g (1돈)" 형태로 만든다. */
    private String resolveDefaultOptionName(String given, ProductPricing pricing) {
        if (given != null && !given.isBlank()) {
            return given;
        }
        if (pricing.weightG() == null) {
            return "기본";
        }
        BigDecimal gram = pricing.weightG().stripTrailingZeros();
        BigDecimal don = pricing.weightG().divide(
                com.hanseongge.global.util.Weight.GRAM_PER_DON, 2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return "%sg (%s돈)".formatted(gram.toPlainString(), don.toPlainString());
    }

    /**
     * 가격 산출 캐시 무효화.
     *
     * <p>가격은 상품의 재료로 계산되므로, 재료가 바뀌면 캐시된 결과는 틀린 값이 된다.
     * 시세 변경은 {@code MarketPriceChangedEvent} 가 같은 캐시를 비운다 — 여기서는
     * 상품 쪽 변경을 담당한다.
     */
    /**
     * 진열 순서를 바꾼다. <b>보낸 상품들끼리만</b> 자리를 바꾼다.
     *
     * <h2>보내지 않은 상품은 제자리에 둔다</h2>
     * 화면은 지금 보이는 것만 보낸다 — 「골드바」 탭이거나, 숨김 상품이 빠진 목록이다.
     * 그래서 <b>보낸 상품들이 지금 차지한 자리</b>를 모아, 그 자리에만 새 차례대로 다시
     * 꽂는다. 다른 상품은 몇 번째였든 그대로다.
     *
     * <p>자리 번호를 그대로 쓰지 않고 전체를 0 부터 다시 매기는 이유는 처음에 모두 0 이기
     * 때문이다. 겹친 번호로는 앞뒤가 등록순으로 결정되어, 끌어다 놓은 자리와 다른 곳에 선다.
     *
     * <h2>카테고리마다 따로 두지 않는다</h2>
     * 상품은 카테고리를 하나만 가진다. 그래서 전체 순서 하나로 모든 탭의 차례가 정해지고,
     * 「골드바」 탭은 그 순서에서 골드바만 걸러낸 것이 된다.
     */
    @Transactional
    @AuditLog(action = AuditActions.PRODUCT_REORDER,
              targetType = AuditActions.TARGET_PRODUCT,
              after = "{'productIds': #productIds}")
    public void reorder(List<Long> productIds, String adminLoginId) {
        if (productIds == null || productIds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "진열 순서가 비어 있습니다.");
        }
        if (new LinkedHashSet<>(productIds).size() != productIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "같은 상품이 두 번 들어 있습니다.");
        }

        Map<Long, Product> moving = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));
        if (moving.size() != productIds.size()) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND,
                    "없는 상품이 들어 있습니다. 새로고침한 뒤 다시 저장해 주세요.");
        }

        // 지금 손님에게 보이는 그 차례. 화면이 본 것과 같은 정렬이어야 자리가 어긋나지 않는다.
        List<Product> shelf = new ArrayList<>(productRepository.findAll(
                Sort.by(Sort.Order.asc("sort"), Sort.Order.desc("id"))));

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < shelf.size(); i++) {
            if (moving.containsKey(shelf.get(i).getId())) {
                slots.add(i);
            }
        }
        for (int i = 0; i < productIds.size(); i++) {
            shelf.set(slots.get(i), moving.get(productIds.get(i)));
        }
        for (int position = 0; position < shelf.size(); position++) {
            shelf.get(position).placeAt(position);
        }
        log.info("[PRODUCT] 진열 순서 변경 {}개. by={}", productIds.size(), adminLoginId);
    }

    private void evictPriceCache() {
        var cache = cacheManager.getCache(CacheConfig.PRODUCT_PRICES);
        if (cache != null) {
            cache.clear();
        }
    }

    private Map<String, Object> snapshot(Product product) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", product.getName());
        values.put("categoryId", product.getCategoryId());
        values.put("metalType", product.getMetalType() == null ? null : product.getMetalType().name());
        values.put("priceType", product.getPriceType().name());
        values.put("weightG", product.getWeightG());
        values.put("laborCost", product.getLaborCost());
        values.put("multiplier", product.getMultiplier());
        values.put("fixedPrice", product.getFixedPrice());
        values.put("status", product.getStatus().name());
        values.put("active", product.isActive());
        return values;
    }

    private Map<String, Object> optionSnapshot(ProductOption option) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", option.getName());
        values.put("weightG", option.getWeightG());
        values.put("additionalLaborCost", option.getAdditionalLaborCost());
        values.put("stock", option.getStock());
        values.put("sort", option.getSort());
        values.put("active", option.isActive());
        return values;
    }
}
