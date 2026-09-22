package com.hanseongge.domain.user.identity;

import java.time.LocalDate;

/**
 * 본인인증 공급자가 돌려준 것 — <b>공급자 계층 밖으로 이 모양 그대로 나가지 않는다.</b>
 *
 * <p>{@link #ci} 는 원본이다. {@code IdentityVerificationService} 가 받자마자 판정 키로 해시하고
 * 이 객체를 버린다. 그래서 여기 있는 값은 DB 에도, 로그에도, 토큰에도 들어가지 않는다
 * ({@link #toString} 도 가린다).
 *
 * <p>공급자 고유 규격(이니시스의 SEED·txId·authRequestUrl 등)은 공급자 구현 안에서 끝난다.
 * 다른 업체(NICE 등)로 바뀌어도 이 모양만 맞추면 서비스 계층은 그대로다.
 *
 * @param providerTxId 공급자 거래번호. 분쟁 시 공급자 기록과 대조하는 유일한 열쇠다
 * @param ci           연계정보 원본. 수단에 따라 오지 않을 수 있다(이니시스 규격: 카카오 "제한적 제공")
 * @param name         실명
 * @param birthDate    생년월일
 * @param phone        휴대폰 번호
 * @param carrier      통신사. 없을 수 있다
 */
public record IdentityVerificationResult(
        String providerTxId,
        String ci,
        String name,
        LocalDate birthDate,
        String phone,
        String carrier) {

    public IdentityVerificationResult {
        if (providerTxId == null || providerTxId.isBlank()) {
            throw new IllegalArgumentException("본인인증 응답에 거래번호가 없습니다.");
        }
    }

    public boolean hasCi() {
        return ci != null && !ci.isBlank();
    }

    /** 예외 메시지·로그에 CI·실명·번호가 섞이지 않게 한다. record 기본 toString 은 전부 찍는다. */
    @Override
    public String toString() {
        return "IdentityVerificationResult[providerTxId=" + providerTxId + ", ci=" + (hasCi() ? "***" : "none") + "]";
    }
}
