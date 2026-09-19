package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.entity.AgreementType;
import com.hanseongge.domain.user.oauth.OAuthUserInfo;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 가입이 진행 중인 상태 — 아직 계정은 없다.
 *
 * <p>이 값이 서명된 {@code signUpToken} 에 담겨 브라우저를 왕복한다. 순서는
 * <b>약관 → 본인확인 → (비밀번호·이메일) → 계정 생성</b>이고, 계정은 마지막에 한 번에 만든다.
 *
 * <h2>왜 약관이 본인확인보다 먼저인가</h2>
 * 본인확인은 이름·생년월일·휴대폰을 받는 <b>개인정보 수집</b>이다. 수집 전에 동의가 있어야
 * 한다. 본인확인 창에 뜨는 약관은 통신사·인증기관의 것이지 우리 서비스 약관이 아니다.
 *
 * <h2>⚠️ 본인확인 결과를 여기 담지 않는다</h2>
 * <b>JWT 는 서명될 뿐 암호화되지 않는다.</b> 이름·생년월일·DI 를 claim 에 넣으면 브라우저에서
 * Base64 디코딩만으로 전부 보이고, 그 값이 sessionStorage 에 남는다. 그래서 신원은
 * DB({@code identity_verifications})에 두고 여기에는 {@link #identityVerificationId} 만 담는다.
 *
 * <p>소셜 제공자가 준 이메일·이름은 담는다. 사용자가 방금 제공자 화면에서 직접 고른 값이고
 * 우리 응답으로 이미 한 번 나갔던 값이라, 새로 노출되는 것이 없다.
 *
 * @param social                 소셜 가입일 때만. 자체 가입은 null
 * @param agreements             약관 동의 내역. 약관 단계 전이면 null
 * @param identityRequestId      본인확인 창을 연 거래번호. 돌아온 결과가 이 요청의 것인지 대조한다
 * @param identityVerificationId 본인확인 전이면 null, 마쳤으면 그 행의 id
 */
public record PendingSignUp(
        OAuthUserInfo social,
        Map<AgreementType, Boolean> agreements,
        String identityRequestId,
        Long identityVerificationId) {

    public PendingSignUp {
        if (agreements != null) {
            EnumMap<AgreementType, Boolean> copy = new EnumMap<>(AgreementType.class);
            copy.putAll(agreements);
            agreements = Collections.unmodifiableMap(copy);
        }
    }

    public static PendingSignUp self() {
        return new PendingSignUp(null, null, null, null);
    }

    public static PendingSignUp social(OAuthUserInfo social) {
        return new PendingSignUp(social, null, null, null);
    }

    public boolean isSocial() {
        return social != null;
    }

    public boolean hasAgreements() {
        return agreements != null;
    }

    public boolean isIdentityVerified() {
        return identityVerificationId != null;
    }

    public PendingSignUp withAgreements(Map<AgreementType, Boolean> agreements) {
        return new PendingSignUp(social, agreements, identityRequestId, identityVerificationId);
    }

    public PendingSignUp withIdentityRequest(String identityRequestId) {
        return new PendingSignUp(social, agreements, identityRequestId, identityVerificationId);
    }

    public PendingSignUp withIdentity(Long identityVerificationId) {
        return new PendingSignUp(social, agreements, identityRequestId, identityVerificationId);
    }
}
