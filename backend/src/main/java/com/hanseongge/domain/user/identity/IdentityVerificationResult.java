package com.hanseongge.domain.user.identity;

import com.hanseongge.domain.user.entity.VerifiedIdentity;

/**
 * 본인확인 공급자가 돌려준 것 전부.
 *
 * <p>{@link VerifiedIdentity} 는 <b>회원에 남길 값</b>이고, 여기에는 이력에만 필요한
 * 값({@link #providerTxId}·{@link #carrier})이 더 붙는다. 둘을 한 덩어리로 만들지 않는
 * 이유는 회원 테이블에 거래번호가 들어갈 자리가 없기 때문이다.
 *
 * <p><b>CI 필드가 없다.</b> 공급자가 주더라도 받지 않는다 — 2024-08-14 시행 정보통신망법
 * §23조의5·6 으로 CI 이용기관에 직접 의무와 형벌이 생겼다. 받아서 버리는 것과 처음부터
 * 받을 자리를 두지 않는 것은 다르다. 자리가 있으면 언젠가 저장된다.
 *
 * @param providerTxId 공급자 거래번호. 분쟁 시 공급자 기록과 대조하는 유일한 열쇠다
 * @param carrier      통신사. 없을 수 있다
 */
public record IdentityVerificationResult(
        String providerTxId,
        VerifiedIdentity identity,
        String carrier) {

    public IdentityVerificationResult {
        if (providerTxId == null || providerTxId.isBlank()) {
            throw new IllegalArgumentException("본인확인 응답에 거래번호가 없습니다.");
        }
        if (identity == null) {
            throw new IllegalArgumentException("본인확인 응답에 신원이 없습니다.");
        }
    }
}
