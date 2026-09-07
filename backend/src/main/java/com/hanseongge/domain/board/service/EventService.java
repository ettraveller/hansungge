package com.hanseongge.domain.board.service;

import com.hanseongge.domain.board.entity.Event;
import com.hanseongge.domain.board.entity.EventEntry;
import com.hanseongge.domain.board.repository.EventEntryRepository;
import com.hanseongge.domain.board.repository.EventRepository;
import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.repository.UserRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.audit.AuditContext;
import com.hanseongge.global.audit.AuditLog;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import com.hanseongge.global.util.PrivacyMask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 이벤트 + 응모.
 *
 * <p>관리자가 이벤트를 열고 고객이 응모한다. 레퍼런스는 응모글만 쌓이는 게시판이지만,
 * 그러면 무슨 이벤트인지가 화면 어디에도 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final EventEntryRepository entryRepository;
    private final UserRepository userRepository;

    // =================================================================
    // 이벤트
    // =================================================================

    @Transactional(readOnly = true)
    public Page<Event> findPublicPage(Pageable pageable) {
        return eventRepository.findPublicPage(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Event> findAdminPage(Pageable pageable) {
        return eventRepository.findAdminPage(pageable);
    }

    @Transactional(readOnly = true)
    public Event findPublic(Long id) {
        return eventRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND,
                        "노출 불가 이벤트 조회: " + id));
    }

    @Transactional(readOnly = true)
    public Event find(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND,
                        "이벤트 없음: " + id));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increaseViewCount(Long id) {
        eventRepository.findByIdAndActiveTrue(id).ifPresent(Event::increaseViewCount);
    }

    @Transactional
    @AuditLog(action = AuditActions.EVENT_CREATE,
              targetType = AuditActions.TARGET_EVENT,
              targetId = "#result.getId()")
    public Event create(String title, String content, String thumbnailKey,
                        OffsetDateTime startAt, OffsetDateTime endAt) {
        validatePeriod(startAt, endAt);

        Event saved = eventRepository.save(Event.create(title, content, thumbnailKey, startAt, endAt));
        log.info("[EVENT] 등록 id={} 기간={}~{}", saved.getId(), startAt, endAt);
        return saved;
    }

    @Transactional
    @AuditLog(action = AuditActions.EVENT_UPDATE,
              targetType = AuditActions.TARGET_EVENT,
              targetId = "#id")
    public Event update(Long id, String title, String content, String thumbnailKey,
                        OffsetDateTime startAt, OffsetDateTime endAt, boolean active) {
        validatePeriod(startAt, endAt);

        Event event = find(id);
        AuditContext.before(snapshot(event));

        event.update(title, content, thumbnailKey, startAt, endAt, active);
        return event;
    }

    @Transactional
    @AuditLog(action = AuditActions.EVENT_HIDE,
              targetType = AuditActions.TARGET_EVENT,
              targetId = "#id",
              after = "{'active': false}")
    public void hide(Long id) {
        Event event = find(id);
        AuditContext.before(snapshot(event));

        event.hide();
        log.info("[EVENT] 내림 id={}", id);
    }

    /**
     * 기간 검증.
     *
     * <p>끝이 시작보다 앞서면 {@code status()} 가 영원히 {@code CLOSED} 를 돌려주고,
     * 관리자는 "이벤트를 등록했는데 종료로 뜬다"만 보게 된다. 등록 시점에 막는다.
     */
    private void validatePeriod(OffsetDateTime startAt, OffsetDateTime endAt) {
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이벤트 종료가 시작보다 빠름: %s ~ %s".formatted(startAt, endAt));
        }
    }

    // =================================================================
    // 응모
    // =================================================================

    @Transactional(readOnly = true)
    public Page<EventEntry> findEntries(Long eventId, Pageable pageable) {
        return entryRepository.findPublicPage(eventId, pageable);
    }

    @Transactional(readOnly = true)
    public long countEntries(Long eventId) {
        return entryRepository.countByEventIdAndActiveTrue(eventId);
    }

    /** 목록용 — 이벤트별 응모 수를 한 번에 모아 온다. */
    @Transactional(readOnly = true)
    public Map<Long, Long> countEntriesByEvents(List<Event> events) {
        List<Long> ids = events.stream().map(Event::getId).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : entryRepository.countByEventIds(ids)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    @Transactional(readOnly = true)
    public boolean hasEntered(Long eventId, Long userId) {
        return userId != null && entryRepository.existsByEventIdAndUserIdAndActiveTrue(eventId, userId);
    }

    /**
     * 응모.
     *
     * <p>기간 밖이면 거절한다. 화면에서 버튼을 감추는 것만으로는 부족하다 — 종료 직전에
     * 열어 둔 화면에서 누르거나 API 를 직접 부르면 그대로 들어온다.
     *
     * <p>중복 응모도 막는다. 한 사람이 100번 응모해서 당첨 확률을 올리는 것을 막지 않으면
     * 이벤트 자체가 성립하지 않는다.
     */
    @Transactional
    public EventEntry enter(Long eventId, Long userId, String content, boolean secret) {
        Event event = findPublic(eventId);

        if (!event.isOpen()) {
            throw new BusinessException(ErrorCode.EVENT_NOT_OPEN,
                    "기간 밖 응모: eventId=%d status=%s".formatted(eventId, event.status()));
        }
        if (entryRepository.existsByEventIdAndUserIdAndActiveTrue(eventId, userId)) {
            throw new BusinessException(ErrorCode.EVENT_ALREADY_ENTERED,
                    "중복 응모: eventId=%d user=%d".formatted(eventId, userId));
        }

        EventEntry saved = entryRepository.save(EventEntry.create(eventId, userId, content, secret));
        log.info("[EVENT] 응모 id={} event={} user={}", saved.getId(), eventId, userId);
        return saved;
    }

    @Transactional
    public void withdrawEntry(Long entryId, Long userId) {
        EventEntry entry = entryRepository.findByIdAndActiveTrue(entryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "응모 없음: " + entryId));

        if (!entry.isOwnedBy(userId)) {
            // 남의 응모면 존재를 알리지 않는다
            throw new BusinessException(ErrorCode.NOT_FOUND,
                    "타인 응모 취소 시도: id=%d user=%s".formatted(entryId, userId));
        }
        entry.withdraw();
    }

    /** 응모자 아이디 → 가려진 이름. {@code QnaService} 와 같은 방식이다. */
    @Transactional(readOnly = true)
    public Map<Long, String> maskedEntrantNames(List<EventEntry> entries) {
        List<Long> userIds = entries.stream().map(EventEntry::getUserId).distinct().toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        user -> PrivacyMask.name(user.getName()),
                        (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, Object> snapshot(Event event) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", event.getTitle());
        values.put("startAt", String.valueOf(event.getStartAt()));
        values.put("endAt", String.valueOf(event.getEndAt()));
        values.put("active", event.isActive());
        return values;
    }
}
