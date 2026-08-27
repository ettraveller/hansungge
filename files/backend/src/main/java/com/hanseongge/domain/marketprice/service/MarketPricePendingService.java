package com.hanseongge.domain.marketprice.service;

import com.hanseongge.domain.audit.dto.AuditRecord;
import com.hanseongge.domain.audit.entity.ActorType;
import com.hanseongge.domain.audit.service.AuditLogService;
import com.hanseongge.domain.marketprice.entity.MarketPricePending;
import com.hanseongge.domain.marketprice.entity.PendingStatus;
import com.hanseongge.domain.marketprice.repository.MarketPricePendingRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * L4 — 이상 변동 승인/반려 (개발계획 §12.4).
 *
 * <p>승인해야만 시세가 발행된다. 반려하면 <b>아무 일도 일어나지 않는다</b> — 현재 시세는
 * 계속 직전 값이고, 원본은 {@code ANOMALY} 표시가 붙은 채 그대로 남는다.
 * "무시했다"가 아니라 "판단해서 적용하지 않았다"가 기록으로 남는 것이 반려의 목적이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketPricePendingService {

    private final MarketPricePendingRepository pendingRepository;
    private final MarketPriceService marketPriceService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<MarketPricePending> findByStatus(PendingStatus status, Pageable pageable) {
        return pendingRepository.findByStatusOrderByCreatedAtDescIdDesc(status, pageable);
    }

    @Transactional(readOnly = true)
    public MarketPricePending find(Long id) {
        return pendingRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRICE_PENDING_NOT_FOUND,
                        "승인 대기 건 없음: " + id));
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return pendingRepository.countByStatus(PendingStatus.PENDING);
    }

    /**
     * 승인 → 보류됐던 값을 최종 시세로 발행한다.
     *
     * @param reason 승인 사유. 이 값이 발행되는 시세의 {@code change_reason} 이 된다
     */
    @Transactional
    public PriceApplyResult approve(Long id, String reason, String adminLoginId) {
        MarketPricePending pending = find(id);
        decide(pending, adminLoginId, true);

        PriceApplyResult result = marketPriceService.applyApproved(
                pending, "[승인] " + reason, adminLoginId);

        auditLogService.record(AuditRecord.builder(AuditActions.PRICE_PENDING_APPROVE)
                .actor(ActorType.ADMIN, adminLoginId)
                .target(AuditActions.TARGET_MARKET_PRICE,
                        pending.getMetalType() + "/" + pending.getPriceSide())
                .before(beforeSnapshot(pending))
                .after(decision("APPROVED", reason, result))
                .build());

        log.info("[MARKET-PRICE] 승인 — id={} {}/{} {} 적용 by={}",
                id, pending.getMetalType(), pending.getPriceSide(), pending.getCalcPrice(), adminLoginId);
        return result;
    }

    /**
     * 반려 → <b>시세는 변하지 않는다.</b>
     *
     * <p>V2 에는 {@code reject_reason} 컬럼이 없어(V6/V7 보강 항목) 사유는 감사로그에만 남는다.
     */
    @Transactional
    public MarketPricePending reject(Long id, String reason, String adminLoginId) {
        MarketPricePending pending = find(id);
        decide(pending, adminLoginId, false);

        auditLogService.record(AuditRecord.builder(AuditActions.PRICE_PENDING_REJECT)
                .actor(ActorType.ADMIN, adminLoginId)
                .target(AuditActions.TARGET_MARKET_PRICE,
                        pending.getMetalType() + "/" + pending.getPriceSide())
                .before(beforeSnapshot(pending))
                .after(decision("REJECTED", reason, null))
                .build());

        log.info("[MARKET-PRICE] 반려 — id={} {}/{} 시세 변경 없음 by={} 사유={}",
                id, pending.getMetalType(), pending.getPriceSide(), adminLoginId, reason);
        return pending;
    }

    /**
     * 상태 전이 + 사유 필수 검사.
     *
     * <p>엔티티가 던지는 {@link IllegalStateException}(이미 처리됨)을 도메인 오류로 바꾼다.
     * 그대로 두면 승인 버튼을 두 번 누른 관리자에게 500 이 나간다 — 실제로는 409 다.
     */
    private void decide(MarketPricePending pending, String adminLoginId, boolean approve) {
        if (pending.getStatus() != PendingStatus.PENDING) {
            throw new BusinessException(ErrorCode.PRICE_PENDING_ALREADY_DECIDED,
                    "이미 처리됨: id=%d status=%s".formatted(pending.getId(), pending.getStatus()));
        }
        if (approve) {
            pending.approve(adminLoginId);
        } else {
            pending.reject(adminLoginId);
        }
    }

    private Map<String, Object> beforeSnapshot(MarketPricePending pending) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("pendingId", pending.getId());
        values.put("calcPricePerGram", pending.getCalcPrice());
        values.put("prevPricePerGram", pending.getPrevPrice());
        values.put("changePercent", pending.getChangePercent());
        return values;
    }

    private Map<String, Object> decision(String status, String reason, PriceApplyResult result) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("decision", status);
        values.put("reason", reason);
        values.put("effectiveId", result == null || result.effective() == null
                ? null : result.effective().getId());
        return values;
    }
}
