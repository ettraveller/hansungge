package com.hanseongge.domain.user.entity;

import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 회원의 본인인증 상태 — 자체 가입자와 소셜 가입자가 어떻게 다른가 (2026-09-21 개편).
 */
class UserIdentityTest {

    private static VerifiedIdentity person(String key, String name, String phone) {
        return new VerifiedIdentity(key, null, name, LocalDate.of(1990, 1, 1), phone);
    }

    /** DI 까지 있는 사람. CI 를 주지 않는 수단과 섞어 시험할 때 쓴다. */
    private static VerifiedIdentity person(String key, String duplicateKey, String name, String phone) {
        return new VerifiedIdentity(key, duplicateKey, name, LocalDate.of(1990, 1, 1), phone);
    }

    @Test
    @DisplayName("소셜 가입자는 본인인증 전이다 — 판정 키·휴대폰·비밀번호가 없어 휴대폰 로그인이 안 된다")
    void socialSignUpHasNoIdentity() {
        User user = User.signUpSocial("buyer@test.local", "구글닉네임");

        assertThat(user.isIdentityVerified()).isFalse();
        assertThat(user.getIdentityKey()).isNull();
        assertThat(user.getPhone()).isNull();
        assertThat(user.canPasswordLogin()).isFalse();
        assertThat(user.getName()).isEqualTo("구글닉네임");
    }

    @Test
    @DisplayName("자체 가입자는 본인인증 값으로 만들어지고 휴대폰 로그인이 된다")
    void verifiedSignUp() {
        User user = User.signUpVerified(null, false, person("KEY-1", "홍길동", "010-1234-5678"), "SKT", "$2a$hash");

        assertThat(user.isIdentityVerified()).isTrue();
        assertThat(user.getIdentityKey()).isEqualTo("KEY-1");
        assertThat(user.getPhone()).isEqualTo("01012345678");
        assertThat(user.canPasswordLogin()).isTrue();
    }

    @Test
    @DisplayName("자체 가입에는 비밀번호가 있어야 한다 — 로그인할 방법이 없는 계정을 만들지 않는다")
    void verifiedSignUpNeedsPassword() {
        assertThatThrownBy(() -> User.signUpVerified(null, false, person("KEY-1", "홍길동", "01012345678"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("소셜 가입자가 본인인증하면 실명·휴대폰이 채워지고 검증 안 된 이름이 바뀐다")
    void socialMemberVerifies() {
        User user = User.signUpSocial(null, "구글닉네임");

        user.applyVerifiedIdentity(person("KEY-1", "홍길동", "01012345678"), null);

        assertThat(user.isIdentityVerified()).isTrue();
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getPhone()).isEqualTo("01012345678");
        assertThat(user.getBirthDate()).isEqualTo(LocalDate.of(1990, 1, 1));
    }

    @Test
    @DisplayName("이미 본인인증한 회원에 다른 사람의 결과를 넣으면 거부한다 — 호출자의 실수가 곧 계정 탈취다")
    void otherPersonIsRejected() {
        User user = User.signUpVerified(null, false, person("KEY-1", "홍길동", "01012345678"), null, "$2a$hash");

        assertThatThrownBy(() -> user.applyVerifiedIdentity(person("KEY-2", "남의이름", "01099998888"), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.IDENTITY_MISMATCH);
        assertThat(user.getName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("같은 사람이 번호를 바꿔 다시 인증하면 번호가 새것으로 맞춰진다")
    void samePersonNewNumber() {
        User user = User.signUpVerified(null, false, person("KEY-1", "홍길동", "01011111111"), null, "$2a$hash");

        user.applyVerifiedIdentity(person("KEY-1", "홍길동", "01022222222"), null);

        assertThat(user.getPhone()).isEqualTo("01022222222");
    }

    @Test
    @DisplayName("번호를 그대로 두는 반영 — 같은 사람의 다른 계정이 그 번호로 로그인하고 있을 때")
    void keepingPhone() {
        User user = User.signUpSocial(null, "구글닉네임");

        user.applyVerifiedIdentityKeepingPhone(person("KEY-1", "홍길동", "01012345678"), null);

        assertThat(user.isIdentityVerified()).isTrue();
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(user.getPhone()).isNull();
    }

    @Test
    @DisplayName("비밀번호만 있고 번호가 없으면 휴대폰 로그인이 안 된다")
    void passwordWithoutPhone() {
        User user = User.signUpSocial(null, null);
        user.attachPassword("$2a$hash");

        assertThat(user.hasPassword()).isTrue();
        assertThat(user.canPasswordLogin()).isFalse();
    }
}
