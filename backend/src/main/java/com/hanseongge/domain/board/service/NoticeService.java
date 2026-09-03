package com.hanseongge.domain.board.service;

import com.hanseongge.domain.board.entity.Notice;
import com.hanseongge.domain.board.repository.NoticeRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.audit.AuditContext;
import com.hanseongge.global.audit.AuditLog;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 공지사항 (설계서 §5 콘텐츠).
 *
 * <h2>공개 조회와 관리자 조회를 메서드부터 나눈다</h2>
 * {@code findPublicPage} 는 {@code active=true} 를 <b>서버가 강제</b>한다. 하나의 메서드에
 * 불리언 파라미터를 두면 공개 컨트롤러가 그 값을 잘못 넘기는 날 내린 공지가 그대로
 * 노출된다 — 상품({@code ProductSearchCondition.forPublic()})과 같은 이유다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeRepository noticeRepository;

    // ---------- 조회 ----------

    @Transactional(readOnly = true)
    public Page<Notice> findPublicPage(Pageable pageable) {
        return noticeRepository.findPublicPage(pageable);
    }

    @Transactional(readOnly = true)
    public List<Notice> findPublicTop(int size) {
        return noticeRepository.findPublicTop(PageRequest.of(0, size));
    }

    @Transactional(readOnly = true)
    public Page<Notice> findAdminPage(Pageable pageable) {
        return noticeRepository.findAdminPage(pageable);
    }

    /**
     * 공개 상세.
     *
     * <p>내린 공지는 <b>404</b>다. 403 이면 "그 자리에 무언가 있다"가 새어 나가고, 아이디를
     * 훑어 내린 공지 목록을 만들 수 있다 — 숨김 상품과 같은 판단이다.
     */
    @Transactional(readOnly = true)
    public Notice findPublic(Long id) {
        return noticeRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND,
                        "노출 불가 공지 조회: " + id));
    }

    @Transactional(readOnly = true)
    public Notice find(Long id) {
        return noticeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTICE_NOT_FOUND,
                        "공지 없음: " + id));
    }

    /**
     * 조회수 증가.
     *
     * <p>상세 조회와 <b>트랜잭션을 나눈다.</b> 조회는 {@code readOnly=true} 라 같은 자리에서
     * 쓸 수 없고, 하나로 합치려면 상세 조회 전체를 쓰기 트랜잭션으로 바꿔야 한다.
     *
     * <p>{@code REQUIRES_NEW} 인 이유는 <b>조회수 실패가 본문 조회를 막지 않게</b> 하기
     * 위해서다. 조회수는 참고용 숫자고 공지 본문은 고객이 보러 온 것이다. 둘의 중요도가
     * 다르므로 실패의 결과도 달라야 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increaseViewCount(Long id) {
        noticeRepository.findByIdAndActiveTrue(id).ifPresent(Notice::increaseViewCount);
    }

    // ---------- 변경 ----------

    @Transactional
    @AuditLog(action = AuditActions.NOTICE_CREATE,
              targetType = AuditActions.TARGET_NOTICE,
              targetId = "#result.getId()")
    public Notice create(String title, String content, boolean pinned, String adminLoginId) {
        Notice saved = noticeRepository.save(Notice.create(title, content, pinned, adminLoginId));
        log.info("[NOTICE] 등록 id={} pinned={} by={}", saved.getId(), pinned, adminLoginId);
        return saved;
    }

    @Transactional
    @AuditLog(action = AuditActions.NOTICE_UPDATE,
              targetType = AuditActions.TARGET_NOTICE,
              targetId = "#id")
    public Notice update(Long id, String title, String content, boolean pinned, boolean active) {
        Notice notice = find(id);
        AuditContext.before(snapshot(notice));

        notice.update(title, content, pinned, active);
        return notice;
    }

    /**
     * 내리기 — 행을 지우지 않는다.
     *
     * <p>공지는 "그때 그렇게 안내했다"는 근거로 남는 문서다. 지우면 무엇을 안내했는지
     * 확인할 방법이 사라지고, 그 확인이 필요해지는 상황은 대개 분쟁이 생긴 뒤다.
     */
    @Transactional
    @AuditLog(action = AuditActions.NOTICE_HIDE,
              targetType = AuditActions.TARGET_NOTICE,
              targetId = "#id",
              after = "{'active': false}")
    public void hide(Long id) {
        Notice notice = find(id);
        AuditContext.before(snapshot(notice));

        notice.update(notice.getTitle(), notice.getContent(), notice.isPinned(), false);
        log.info("[NOTICE] 내림 id={}", id);
    }

    private Map<String, Object> snapshot(Notice notice) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", notice.getTitle());
        values.put("pinned", notice.isPinned());
        values.put("active", notice.isActive());
        return values;
    }
}
