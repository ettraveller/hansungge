package com.hanseongge.domain.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hanseongge.domain.audit.dto.AuditLogSearchCondition;
import com.hanseongge.domain.audit.dto.AuditRecord;
import com.hanseongge.domain.audit.entity.AuditLog;
import com.hanseongge.domain.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 감사로그 기록/조회.
 *
 * <h2>왜 {@code REQUIRES_NEW} 인가</h2>
 * 감사 대상 작업은 실패하면서 롤백되는 경우가 많다 — 로그인 실패, 권한 위반, 검증 실패.
 * 같은 트랜잭션에 감사로그를 쓰면 <b>정확히 그 순간의 기록만 사라진다.</b> 남겨야 할
 * 이유가 가장 큰 사건이 남지 않는 것이다. 그래서 별도 트랜잭션으로 분리해 커밋한다.
 *
 * <h2>감사 실패가 업무를 막지 않는다</h2>
 * 기록 자체가 실패하면 ERROR 로그만 남기고 예외를 삼킨다. 감사로그 테이블 장애로
 * 관리자 로그인·주문 처리가 통째로 멈추는 편이 더 큰 사고이기 때문이다.
 * 대신 삼킨 사실을 ERROR 로 남겨 파일 로그에는 반드시 흔적이 남게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    /** 이 이름이 들어간 키의 값은 JSON 으로 만들지 않는다. */
    private static final List<String> MASKED_KEY_PARTS =
            List.of("password", "secret", "token", "hash", "credential", "authorization");
    private static final String MASKED = "***";

    /** audit_logs.user_agent 컬럼 길이. 넘치면 DB 가 거부하므로 미리 자른다. */
    private static final int USER_AGENT_MAX = 300;
    private static final int ACTOR_MAX = 100;
    private static final int TARGET_ID_MAX = 60;

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditRecord command) {
        try {
            AuditLog entry = AuditLog.of(
                    command.actorType(),
                    cut(command.actor(), ACTOR_MAX),
                    command.action(),
                    command.targetType(),
                    cut(command.targetId(), TARGET_ID_MAX),
                    toJson(command.before()),
                    toJson(command.after()),
                    command.ip(),
                    cut(command.userAgent(), USER_AGENT_MAX));
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("[AUDIT] 감사로그 기록 실패 — action={} actor={} target={}/{}",
                    command.action(), command.actor(), command.targetType(), command.targetId(), e);
        }
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> search(AuditLogSearchCondition condition, Pageable pageable) {
        return auditLogRepository.search(condition, pageable);
    }

    // ---------- 직렬화 ----------

    /**
     * 임의 객체 → JSON 문자열. 비밀번호류 키는 값을 지운다.
     *
     * <p>마스킹을 호출부가 아니라 여기서 하는 이유: 호출부에 맡기면 한 곳만 빠뜨려도
     * 비밀번호 해시가 감사로그에 영구 보존된다. 감사로그는 삭제하지 않는 테이블이다.
     */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.valueToTree(value);
            mask(node);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("[AUDIT] 값 직렬화 실패. 해당 필드를 비웁니다. type={}", value.getClass().getName(), e);
            return null;
        }
    }

    private void mask(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            // 키 목록을 먼저 확정한 뒤 수정한다. 순회 중 put 은 구현에 따라 위험하다.
            List<String> names = new ArrayList<>();
            obj.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (isMaskedKey(name)) {
                    obj.put(name, MASKED);
                } else {
                    mask(obj.get(name));
                }
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(this::mask);
        }
    }

    private boolean isMaskedKey(String key) {
        String lower = key.toLowerCase();
        return MASKED_KEY_PARTS.stream().anyMatch(lower::contains);
    }

    private String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
