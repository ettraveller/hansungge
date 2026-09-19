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
 * @param identityVerificationId 휴대폰 본인확인. <b>모든 가입에 필수다</b>
 * @param social                 소셜 가입일 때만
 * @param rawPassword            자체 가입일 때만. 해싱은 서비스가 한다
 * @param email                  선택. 자체 가입은 인증번호를 통과한 주소, 소셜은 제공자가 준 주소
 * @param emailCodeId            자체 가입에서 이메일을 넣었을 때 통과한 인증번호 행. 소진 표시용
 */
public record SignUpMaterial(
        Long identityVerificationId,
        OAuthUserInfo social,
        String rawPassword,
        String email,
        Long emailCodeId) {

    public SignUpMaterial {
        if (identityVerificationId == null) {
            throw new IllegalArgumentException("본인확인 없이 가입할 수 없습니다.");
        }
        if (social == null && rawPassword == null) {
            throw new IllegalArgumentException("소셜 연동도 비밀번호도 없으면 로그인할 방법이 없습니다.");
        }
    }

    public boolean isSocial() {
        return social != null;
    }

    /** 이메일 소유가 확인됐는가 — 인증번호를 통과했거나 소셜 제공자가 준 값. */
    public boolean isEmailVerified() {
        return email != null && (emailCodeId != null || social != null);
    }
}
