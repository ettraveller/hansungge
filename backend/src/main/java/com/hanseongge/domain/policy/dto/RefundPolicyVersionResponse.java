package com.hanseongge.domain.policy.dto;

import com.hanseongge.domain.policy.entity.RefundPolicy;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** 관리자용 — 개정 이력까지 담는다. */
@Schema(name = "RefundPolicyVersionResponse", description = "환불규정 판 (관리자)")
public record RefundPolicyVersionResponse(

        @Schema(description = "판 번호", example = "3")
        Long id,

        @Schema(description = "규정 전문")
        String content,

        @Schema(description = "개정 사유", example = "반품 검수 기간을 7일로 명시")
        String changeNote,

        @Schema(description = "발행한 관리자", example = "hsgmaster")
        String createdBy,

        @Schema(description = "발행 시각")
        OffsetDateTime createdAt
) {

    public static RefundPolicyVersionResponse from(RefundPolicy policy) {
        return new RefundPolicyVersionResponse(policy.getId(), policy.getContent(),
                policy.getChangeNote(), policy.getCreatedBy(), policy.getCreatedAt());
    }
}
