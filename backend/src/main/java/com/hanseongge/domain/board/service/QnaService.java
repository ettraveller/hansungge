package com.hanseongge.domain.board.service;

import com.hanseongge.domain.board.entity.Qna;
import com.hanseongge.domain.board.entity.QnaBoardType;
import com.hanseongge.domain.board.repository.QnaRepository;
import com.hanseongge.domain.product.entity.Product;
import com.hanseongge.domain.product.repository.ProductRepository;
import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.repository.UserRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.audit.AuditContext;
import com.hanseongge.global.audit.AuditLog;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import com.hanseongge.global.util.NameMasker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 문의 (문의게시판 · 제품문의).
 *
 * <h2>두 게시판이 한 서비스다</h2>
 * 화면은 둘이지만 규칙이 같다 — 목록은 공개, 본문은 비밀글 여부로 갈리고, 답변은
 * 관리자가 단다. 서비스를 나누면 그 규칙이 두 벌이 되고, 한쪽만 고쳐지는 날이 온다.
 *
 * <h2>이름·상품명은 한 번에 모아 온다</h2>
 * 목록 한 페이지가 15줄인데 줄마다 회원과 상품을 따로 조회하면 요청 하나가 31번의
 * 질의가 된다. 아이디를 모아 {@code findAllById} 로 두 번만 부른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QnaService {

    private final QnaRepository qnaRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    // =================================================================
    // 조회
    // =================================================================

    @Transactional(readOnly = true)
    public Page<Qna> findPublicPage(QnaBoardType boardType, Pageable pageable) {
        return qnaRepository.findPublicPage(boardType, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Qna> findMinePage(Long userId, Pageable pageable) {
        return qnaRepository.findMinePage(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Qna> findAdminPage(QnaBoardType boardType, boolean unansweredOnly, Pageable pageable) {
        return qnaRepository.findAdminPage(boardType, unansweredOnly, pageable);
    }

    @Transactional(readOnly = true)
    public long countUnanswered() {
        return qnaRepository.countByAnsweredAtIsNullAndActiveTrue();
    }

    /**
     * 공개 상세.
     *
     * <p>내린 글과 <b>남의 비밀글</b>이 같은 404 다. 403 으로 나누면 "이 번호에 비밀글이
     * 있다"가 드러나고, 아이디를 훑어 비밀 문의가 몇 건인지 셀 수 있다.
     *
     * @param viewerId 로그인하지 않았으면 {@code null}
     */
    @Transactional(readOnly = true)
    public Qna findPublic(Long id, Long viewerId, boolean admin) {
        Qna qna = qnaRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.QNA_NOT_FOUND, "문의 없음: " + id));

        if (!qna.isReadableBy(viewerId, admin)) {
            throw new BusinessException(ErrorCode.QNA_NOT_FOUND,
                    "비밀글 타인 조회: id=%d viewer=%s".formatted(id, viewerId));
        }
        return qna;
    }

    @Transactional(readOnly = true)
    public Qna find(Long id) {
        return qnaRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.QNA_NOT_FOUND, "문의 없음: " + id));
    }

    /** 조회수 증가. {@code NoticeService} 와 같은 이유로 트랜잭션을 나눈다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increaseViewCount(Long id) {
        qnaRepository.findByIdAndActiveTrue(id).ifPresent(Qna::increaseViewCount);
    }

    // =================================================================
    // 변경 (회원)
    // =================================================================

    @Transactional
    public Qna write(Long userId, QnaBoardType boardType, String title, String question,
                     boolean secret, Long productId) {
        if (productId != null) {
            // 없는 상품을 가리키는 문의를 만들지 않는다. 상세 화면에서 상품명이 비어
            // "어느 상품 문의인지 알 수 없는 글"이 된다.
            productRepository.findById(productId).orElseThrow(() ->
                    new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "문의 대상 상품 없음: " + productId));
        }

        Qna saved = qnaRepository.save(
                Qna.create(userId, boardType, productId, title, question, secret));
        log.info("[QNA] 등록 id={} board={} secret={} user={}",
                saved.getId(), boardType, secret, userId);
        return saved;
    }

    /**
     * 작성자 본인의 수정.
     *
     * <p>남의 글이면 {@code QNA_NOT_FOUND} 다 — 403 이면 "그 번호에 남의 글이 있다"가
     * 확인되고, 그것만으로도 존재를 훑을 수 있다.
     */
    @Transactional
    public Qna update(Long id, Long userId, String title, String question,
                      boolean secret, Long productId) {
        Qna qna = findOwned(id, userId);
        qna.update(title, question, secret, qna.getBoardType() == QnaBoardType.PRODUCT ? productId : null);
        return qna;
    }

    @Transactional
    public void withdraw(Long id, Long userId) {
        Qna qna = findOwned(id, userId);
        qna.withdraw();
        log.info("[QNA] 작성자 삭제 id={} user={}", id, userId);
    }

    private Qna findOwned(Long id, Long userId) {
        Qna qna = qnaRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.QNA_NOT_FOUND, "문의 없음: " + id));

        if (!qna.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.QNA_NOT_FOUND,
                    "타인 문의 변경 시도: id=%d user=%s".formatted(id, userId));
        }
        return qna;
    }

    // =================================================================
    // 변경 (관리자)
    // =================================================================

    @Transactional
    @AuditLog(action = AuditActions.QNA_ANSWER,
              targetType = AuditActions.TARGET_QNA,
              targetId = "#id")
    public Qna answer(Long id, String answer, String adminLoginId) {
        Qna qna = find(id);
        AuditContext.before(snapshot(qna));

        qna.answer(answer, adminLoginId);
        log.info("[QNA] 답변 id={} by={}", id, adminLoginId);
        return qna;
    }

    /** 관리자가 내린다. 답변 여부와 무관하다 — 욕설·개인정보 노출은 즉시 내려야 한다. */
    @Transactional
    @AuditLog(action = AuditActions.QNA_HIDE,
              targetType = AuditActions.TARGET_QNA,
              targetId = "#id",
              after = "{'active': false}")
    public void hide(Long id) {
        Qna qna = find(id);
        AuditContext.before(snapshot(qna));

        qna.hideByAdmin();
        log.info("[QNA] 관리자 내림 id={}", id);
    }

    // =================================================================
    // 표시용 이름 모으기
    // =================================================================

    /**
     * 회원 아이디 → <b>가려진</b> 이름.
     *
     * <p>가리는 일을 여기서 끝낸다. 원본 이름을 밖으로 내보내고 DTO 에서 가리게 하면,
     * 새 응답을 만드는 사람이 그 단계를 빠뜨렸을 때 실명이 그대로 나간다.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> maskedAuthorNames(List<Qna> qnas) {
        return authorNames(qnas).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> NameMasker.mask(e.getValue()),
                        (a, b) -> a, LinkedHashMap::new));
    }

    /** 관리자 화면용 — 가리지 않은 이름. */
    @Transactional(readOnly = true)
    public Map<Long, String> authorNames(List<Qna> qnas) {
        List<Long> userIds = qnas.stream().map(Qna::getUserId).distinct().toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        user -> user.getName() == null ? "" : user.getName(),
                        (a, b) -> a, LinkedHashMap::new));
    }

    /** 상품 아이디 → 상품명. 상품이 붙지 않은 문의만 있으면 빈 map 이다. */
    @Transactional(readOnly = true)
    public Map<Long, String> productNames(List<Qna> qnas) {
        List<Long> productIds = qnas.stream()
                .map(Qna::getProductId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Product::getName,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /** 한 건짜리 조회에서도 위 두 map 을 그대로 쓰기 위한 도우미. */
    public static <T> Function<Long, T> lookup(Map<Long, T> map) {
        return key -> key == null ? null : map.get(key);
    }

    private Map<String, Object> snapshot(Qna qna) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("status", qna.isAnswered() ? "ANSWERED" : "PENDING");
        values.put("active", qna.isActive());
        values.put("answer", qna.getAnswer());
        return values;
    }
}
