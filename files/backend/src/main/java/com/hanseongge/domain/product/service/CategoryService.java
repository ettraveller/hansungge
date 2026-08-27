package com.hanseongge.domain.product.service;

import com.hanseongge.domain.product.entity.Category;
import com.hanseongge.domain.product.repository.CategoryRepository;
import com.hanseongge.domain.product.repository.ProductRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.audit.AuditContext;
import com.hanseongge.global.audit.AuditLog;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 카테고리 관리 (설계서 §8 카테고리관리).
 *
 * <h2>삭제를 어렵게 만든 이유</h2>
 * 카테고리는 상품이 가리키는 대상이다. 지워지면 그 상품들이 <b>존재하지 않는 분류</b>를
 * 가리키게 되고, 목록 화면에서 조용히 사라진다. 그래서
 * <ul>
 *   <li>상품이 하나라도 있으면 삭제하지 않는다 — 비활성({@code active=false})으로 숨긴다</li>
 *   <li>하위 카테고리가 있으면 삭제하지 않는다 — 고아 노드를 만들지 않는다</li>
 * </ul>
 * 비활성은 공개 조회에서만 빠지고 기존 상품의 참조는 그대로 유지된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    /** 카테고리 계층 탐색 깊이 상한. 순환 데이터에서도 멈춘다. */
    private static final int MAX_DEPTH = 8;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<Category> findAll() {
        return categoryRepository.findAll(
                org.springframework.data.domain.Sort.by("sort").ascending().and(
                        org.springframework.data.domain.Sort.by("id").ascending()));
    }

    /** 공개용 — 비활성 카테고리는 나가지 않는다. */
    @Transactional(readOnly = true)
    public List<Category> findActive() {
        return categoryRepository.findByActiveTrueOrderBySortAscIdAsc();
    }

    /**
     * 자기 자신 + 모든 하위 카테고리 ID.
     *
     * <p>상위 카테고리를 고른 고객은 <b>그 아래 상품 전부</b>를 기대한다. "골드바"를 눌렀는데
     * "기본 / 선물용" 에 등록된 상품이 안 나오면 화면이 비어 보이고, 운영자는 상품을
     * 상위 카테고리로 옮기는 식으로 우회하게 된다 — 그러면 분류가 무의미해진다.
     *
     * <p>깊이 제한을 두는 이유: 데이터가 순환하면(직접 수정 등) 여기서 무한 루프가 난다.
     * 등록 API 는 순환을 막지만 DB 를 직접 고친 데이터까지 막아주지는 않는다.
     */
    @Transactional(readOnly = true)
    public List<Long> descendantIds(Long rootId) {
        List<Category> all = categoryRepository.findAll();

        List<Long> result = new ArrayList<>();
        result.add(rootId);

        List<Long> frontier = List.of(rootId);
        for (int depth = 0; depth < MAX_DEPTH && !frontier.isEmpty(); depth++) {
            List<Long> parents = frontier;
            List<Long> next = all.stream()
                    .filter(c -> c.getParentId() != null && parents.contains(c.getParentId()))
                    .map(Category::getId)
                    .filter(id -> !result.contains(id))
                    .toList();
            result.addAll(next);
            frontier = next;
        }
        return result;
    }

    @Transactional(readOnly = true)
    public Category find(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND,
                        "카테고리 없음: " + id));
    }

    @Transactional
    @AuditLog(action = AuditActions.CATEGORY_CREATE,
              targetType = AuditActions.TARGET_CATEGORY,
              targetId = "#result.getId()")
    public Category create(Long parentId, String name, int sort) {
        if (parentId != null) {
            find(parentId);   // 존재 확인. 없는 부모를 가리키는 카테고리를 만들지 않는다
        }
        Category saved = categoryRepository.save(Category.create(parentId, name, sort));
        log.info("[CATEGORY] 생성 id={} name={} parentId={}", saved.getId(), name, parentId);
        return saved;
    }

    @Transactional
    @AuditLog(action = AuditActions.CATEGORY_UPDATE,
              targetType = AuditActions.TARGET_CATEGORY,
              targetId = "#id")
    public Category update(Long id, Long parentId, String name, int sort, boolean active) {
        Category category = find(id);
        AuditContext.before(snapshot(category));

        validateParent(id, parentId);
        category.update(parentId, name, sort, active);
        return category;
    }

    @Transactional
    @AuditLog(action = AuditActions.CATEGORY_DELETE,
              targetType = AuditActions.TARGET_CATEGORY,
              targetId = "#id",
              after = "null")
    public void delete(Long id) {
        Category category = find(id);
        AuditContext.before(snapshot(category));

        if (productRepository.existsByCategoryId(id)) {
            throw new BusinessException(ErrorCode.CATEGORY_IN_USE,
                    "상품이 있는 카테고리 삭제 시도: " + id);
        }
        if (!categoryRepository.findByParentIdAndActiveTrueOrderBySortAscIdAsc(id).isEmpty()) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_CHILDREN,
                    "하위 카테고리가 있는 카테고리 삭제 시도: " + id);
        }

        categoryRepository.delete(category);
        log.info("[CATEGORY] 삭제 id={} name={}", id, category.getName());
    }

    /**
     * 부모 지정 검증 — 순환 참조 방지.
     *
     * <p>자기 자신이나 자기 하위를 부모로 지정하면 트리가 고리가 되고, 그 순간
     * 카테고리를 순회하는 모든 코드가 <b>무한 루프</b>에 빠진다. 화면이 멎을 때까지
     * 아무도 눈치채지 못한다.
     */
    private void validateParent(Long id, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(id)) {
            throw new BusinessException(ErrorCode.CATEGORY_CYCLE, "자기 자신을 부모로 지정: " + id);
        }
        find(parentId);

        Long cursor = parentId;
        int guard = 0;
        while (cursor != null) {
            if (cursor.equals(id)) {
                throw new BusinessException(ErrorCode.CATEGORY_CYCLE,
                        "하위 카테고리를 부모로 지정: id=%d parentId=%d".formatted(id, parentId));
            }
            // 이미 고리가 있는 데이터(DB 직접 수정 등)에서도 여기서 멈춰야 한다.
            if (++guard > 32) {
                throw new BusinessException(ErrorCode.CATEGORY_CYCLE,
                        "카테고리 계층이 너무 깊거나 순환합니다: id=" + id);
            }
            cursor = categoryRepository.findById(cursor).map(Category::getParentId).orElse(null);
        }
    }

    private Map<String, Object> snapshot(Category category) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", category.getName());
        values.put("parentId", category.getParentId());
        values.put("sort", category.getSort());
        values.put("active", category.isActive());
        return values;
    }
}
