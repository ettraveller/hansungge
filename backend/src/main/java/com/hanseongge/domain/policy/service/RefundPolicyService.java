package com.hanseongge.domain.policy.service;

import com.hanseongge.domain.policy.dto.RefundPolicyPublishRequest;
import com.hanseongge.domain.policy.dto.RefundPolicyResponse;
import com.hanseongge.domain.policy.dto.RefundPolicyVersionResponse;
import com.hanseongge.domain.policy.entity.RefundPolicy;
import com.hanseongge.domain.policy.repository.RefundPolicyRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.audit.AuditLog;
import com.hanseongge.global.auth.AdminPrincipal;
import com.hanseongge.global.dto.PageResponse;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 환불규정 — 조회와 개정.
 *
 * <p>개정은 기존 판을 고치는 것이 아니라 <b>새 판을 쌓는 것</b>이다. 지난 판은
 * 이미 만들어진 주문들이 "동의한 그 내용"이라 건드릴 수 없다 (DB 트리거가 막는다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundPolicyService {

    private final RefundPolicyRepository refundPolicyRepository;

    /** 쇼핑몰·주문서가 보여주는 현재 판. */
    @Transactional(readOnly = true)
    public RefundPolicyResponse current() {
        return RefundPolicyResponse.from(requireCurrent());
    }

    @Transactional(readOnly = true)
    public RefundPolicyVersionResponse currentForAdmin() {
        return RefundPolicyVersionResponse.from(requireCurrent());
    }

    @Transactional(readOnly = true)
    public PageResponse<RefundPolicyVersionResponse> history(Pageable pageable) {
        return PageResponse.of(refundPolicyRepository.findAllByOrderByIdDesc(pageable),
                RefundPolicyVersionResponse::from);
    }

    /**
     * 새 판 발행.
     *
     * <p>직전 판과 내용이 똑같으면 거절한다 — 저장을 두 번 누르는 실수로 판 번호만
     * 올라가면, 이력에서 "무엇이 바뀌었나"를 읽을 수 없게 된다.
     */
    @Transactional
    @AuditLog(action = AuditActions.REFUND_POLICY_PUBLISH,
              targetType = AuditActions.TARGET_REFUND_POLICY,
              targetId = "#result.id()",
              after = "{'changeNote': #request.changeNote()}")
    public RefundPolicyVersionResponse publish(RefundPolicyPublishRequest request, AdminPrincipal actor) {
        String content = request.content().strip();
        RefundPolicy latest = requireCurrent();
        if (latest.getContent().equals(content)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "직전 판과 내용이 같은 환불규정 발행 시도: " + actor.loginId());
        }

        RefundPolicy saved = refundPolicyRepository.save(
                RefundPolicy.publish(content, request.changeNote().strip(), actor.loginId()));
        log.info("[POLICY] 환불규정 새 판 발행 id={} by={}", saved.getId(), actor.loginId());
        return RefundPolicyVersionResponse.from(saved);
    }

    /** 주문 생성이 "동의한 판이 지금 판인가"를 검사할 때 쓴다. */
    @Transactional(readOnly = true)
    public long currentId() {
        return requireCurrent().getId();
    }

    private RefundPolicy requireCurrent() {
        // V34 시드가 1판을 만들므로 비어 있다는 것은 마이그레이션이 깨졌다는 뜻이다.
        return refundPolicyRepository.findTopByOrderByIdDesc()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "환불규정이 한 판도 없다 (V34 시드 확인)"));
    }
}
