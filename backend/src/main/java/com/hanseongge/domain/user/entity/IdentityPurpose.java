package com.hanseongge.domain.user.entity;

/**
 * 본인확인을 한 이유.
 *
 * <p>목적을 남기는 이유는 <b>재사용을 막기 위해서</b>다. 가입하려고 받은 본인확인을
 * 비밀번호 재설정에 쓸 수 있으면, 가입 창을 띄워 인증만 받아 두었다가 남의 계정
 * 비밀번호를 바꾸는 경로가 생긴다. 목적이 다르면 다른 인증이다.
 *
 * <p>{@code identity_verifications.purpose} CHECK 제약과 값이 일치해야 한다 (V23 {@code chk_iv_purpose}).
 */
public enum IdentityPurpose {

    SIGNUP,
    PASSWORD_RESET,
    /** 고액 주문 시점 재확인. 아직 쓰는 곳이 없다. */
    ORDER,
    /**
     * 로그인한 회원의 본인인증 — 매입 신청·정산계좌 등록처럼 돈이 나가는 일 앞에서 받는다.
     * 소셜 가입자는 여기서 처음으로 신원(판정 키·실명·휴대폰)이 채워진다.
     */
    STEP_UP
}
