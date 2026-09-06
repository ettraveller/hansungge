package com.hanseongge.domain.user.service;

import com.hanseongge.domain.user.oauth.OAuthUserInfo;

/**
 * 가입에 필요한 재료.
 *
 * <p>자체 가입과 소셜 가입이 <b>같은 생성 경로</b>를 쓰게 하려고 둔다. 두 경로를 따로
 * 만들면 약관 기록·중복 검사·세션 발급이 두 벌이 되고, 한쪽만 고치는 사고가 난다.
 *
 * <p>엔티티가 아니라 <b>id 를 담는다.</b> 검증은 트랜잭션 밖에서 미리 했지만, 실제로
 * 소진 표시를 하려면 가입 트랜잭션 안에서 다시 읽어야 한다 — 다른 트랜잭션에서 읽어 온
 * 엔티티에 {@code consume()} 을 불러도 아무 일도 일어나지 않는다.
 *
 * @param email                 가입할 주소. 자체 가입은 인증받은 주소, 소셜은 제공자가 준 주소
 * @param emailVerificationId   자체 가입에서만 있다. 소셜은 제공자가 이미 검증했으므로 null
 * @param identityVerificationId 휴대폰 본인확인. 자체 가입은 필수,
 *                               소셜은 공급자 계약 전(TODO E-4)에 한해 null 이 허용된다
 * @param social                소셜 가입일 때만
 * @param rawPassword           자체 가입일 때만. 해싱은 서비스가 한다
 */
public record SignUpMaterial(
        String email,
        Long emailVerificationId,
        Long identityVerificationId,
        OAuthUserInfo social,
        String rawPassword) {

    public SignUpMaterial {
        if (social == null && rawPassword == null) {
            throw new IllegalArgumentException("소셜 연동도 비밀번호도 없으면 로그인할 방법이 없습니다.");
        }
        // 자체 가입은 본인확인 없이 성립하지 않는다 — 이름도 생년월일도 그것으로만 온다.
        if (social == null && identityVerificationId == null) {
            throw new IllegalArgumentException("자체 가입은 본인확인이 필요합니다.");
        }
    }

    public boolean hasIdentity() {
        return identityVerificationId != null;
    }

    public boolean isSocial() {
        return social != null;
    }
}
