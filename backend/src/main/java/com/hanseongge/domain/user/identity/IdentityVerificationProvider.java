package com.hanseongge.domain.user.identity;

import java.util.Map;

/**
 * 휴대폰 본인확인 제공자 (설계서 원칙 P5 — 외부 연동은 인터페이스 뒤에 둔다).
 *
 * <h2>왜 추상화하나</h2>
 * 본인확인은 계약이 끝나야 운영에 붙는다. 인터페이스 뒤에 두면 목 구현으로 가입 흐름 전체를
 * 만들고 검증해 둔 뒤, 계약 후 설정만 바꾼다.
 *
 * <h2>세 단계</h2>
 * <ol>
 *   <li>{@link #start} — 인증창을 여는 폼. 브라우저가 전송한다
 *   <li>(사용자가 공급자 창에서 PASS·토스 등으로 인증을 마친다)
 *   <li>공급자가 결과를 <b>백엔드 수신 주소</b>로 보낸다 → 백엔드가 그 값을 서명해 화면으로 넘긴다
 *   <li>{@link #verify} — 화면이 그 값을 들고 오면, <b>공급자 서버에 직접 되물어</b> 신원을 가져온다
 * </ol>
 *
 * <h2>미설정 처리</h2>
 * {@code IDENTITY_PROVIDER} 로 고른 구현체만 빈이 된다. 선택됐는데 키가 비어 있으면
 * {@link #isConfigured()} 가 false 를 돌려주고 {@code IdentityVerificationRegistry} 가 503 으로 막는다.
 */
public interface IdentityVerificationProvider {

    /** {@code identity_verifications.provider} 에 그대로 들어간다. */
    String code();

    /** 쓸 수 있는 상태인가. false 인 이유는 대개 환경변수가 비어 있는 것이다. */
    boolean isConfigured();

    /**
     * 인증창을 여는 폼.
     *
     * @param requestId   우리가 만든 거래번호(20자 이하). 결과가 <b>이 요청의 것인지</b> 대조하는 데 쓴다
     * @param callbackUrl 공급자가 결과를 보낼 백엔드 주소
     */
    IdentityStartForm start(String requestId, String callbackUrl);

    /**
     * 돌아온 결과를 공급자에게 되물어 검증하고 신원을 가져온다.
     *
     * <p><b>브라우저가 들고 온 값을 그대로 믿지 않는다.</b> {@code returned} 는 사용자가 고칠 수
     * 있는 경로로 온 값이라, 공급자 서버에 다시 물어 확인한 값만 쓴다. 되물은 결과의 거래번호가
     * {@code requestId} 와 다르면 거부한다 — 남의 인증 결과를 내 가입에 붙이는 경로다.
     *
     * @param requestId {@link #start} 에 넘긴 거래번호. 서명된 가입 토큰에서 꺼낸 값이다
     * @param returned  공급자가 수신 주소로 보낸 값 그대로
     * @throws IdentityVerificationException 취소·검증 실패·통신 실패
     */
    IdentityVerificationResult verify(String requestId, Map<String, String> returned);
}
