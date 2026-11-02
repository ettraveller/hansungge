package com.hanseongge.domain.policy.dto;

import com.hanseongge.domain.policy.entity.RefundPolicy;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * 쇼핑몰에 보여주는 환불규정 현재 판.
 *
 * <p>{@code changeNote}·{@code createdBy} 는 담지 않는다 — 운영 내부 사정이다.
 * {@code id} 는 담는다: 주문서가 "이 판에 동의했다"를 적어 보낼 때 쓴다.
 */
@Schema(name = "RefundPolicyResponse", description = "환불규정 (현재 판)")
public record RefundPolicyResponse(

        @Schema(description = "판 번호. 주문 생성 때 동의한 판으로 보낸다", example = "1")
        Long id,

        @Schema(description = "규정 전문")
        String content,

        @Schema(description = "이 판의 게시 시각")
        OffsetDateTime publishedAt
) {

    public static RefundPolicyResponse from(RefundPolicy policy) {
        return new RefundPolicyResponse(policy.getId(), policy.getContent(), policy.getCreatedAt());
    }
}
