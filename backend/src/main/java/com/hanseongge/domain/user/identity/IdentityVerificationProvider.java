package com.hanseongge.domain.user.identity;

/**
 * 휴대폰 본인확인 제공자 (설계서 원칙 P5 — 외부 연동은 인터페이스 뒤에 둔다).
 *
 * <h2>왜 추상화하나</h2>
 * 본인확인은 <b>계약이 끝나야 실물을 붙일 수 있다.</b> KG이니시스 통합인증을 쓰기로
 * 했지만 심사·계약에 시간이 걸리고, 그동안 가입 흐름 전체를 만들고 검증해야 한다.
 * 인터페이스 뒤에 두면 목 구현으로 개발을 끝내 놓고 계약 후 구현체만 갈아 끼운다.
 *
 * <p>공급자를 바꿀 가능성도 실재한다. 본인확인 단가와 커버리지(PASS·토스 등)는
 * 업체마다 다르고, 결제 PG 를 바꿀 때 함께 옮기는 일도 흔하다.
 *
 * <h2>왜 두 단계인가</h2>
 * 본인확인은 우리 서버가 값을 물어보는 방식이 아니다. <b>사용자가 공급자 창에서 직접</b>
 * 통신사를 고르고 인증을 마치고, 그 결과를 들고 우리에게 돌아온다. 그래서
 * {@link #startUrl}(창을 어디로 띄울 것인가)과 {@link #verify}(돌아온 것이 진짜인가)로
 * 나뉜다.
 *
 * <h2>미설정 처리는 소셜 로그인과 같다</h2>
 * 설정이 없어도 <b>빈으로는 등록된다.</b> 빈에서 빼버리면 "왜 본인확인이 안 되지"를
 * 설정이 아니라 코드에서 찾게 된다. 대신 {@link #isConfigured()} 가 false 를 돌려주고
 * {@code IdentityVerificationRegistry} 가 503 으로 막는다.
 */
public interface IdentityVerificationProvider {

    /** {@code identity_verifications.provider} 에 그대로 들어간다. */
    String code();

    /**
     * 쓸 수 있는 상태인가.
     *
     * <p>false 인 이유는 대개 계약이 끝나지 않아 환경변수가 비어 있는 것이다 (TODO E-4).
     */
    boolean isConfigured();

    /**
     * 사용자를 보낼 인증창 주소.
     *
     * @param requestId 우리가 만든 요청 식별자. 돌아온 결과가 <b>이 요청의 것인지</b> 대조하는 데 쓴다
     * @param returnUrl 인증을 마친 뒤 사용자가 돌아올 우리 쪽 주소
     */
    String startUrl(String requestId, String returnUrl);

    /**
     * 돌아온 결과를 공급자에게 되물어 검증하고 신원을 가져온다.
     *
     * <p><b>브라우저가 들고 온 값을 그대로 믿지 않는다.</b> 사용자가 고칠 수 있는 경로로
     * 온 데이터라, 반드시 공급자 서버에 다시 물어 확인한 값만 쓴다.
     *
     * @param providerToken 공급자가 돌려보낸 조회용 토큰
     * @throws IdentityVerificationException 검증 실패·통신 실패·미구현
     */
    IdentityVerificationResult verify(String requestId, String providerToken);
}
