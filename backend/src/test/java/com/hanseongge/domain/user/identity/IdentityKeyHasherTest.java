package com.hanseongge.domain.user.identity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CI → 판정 키. 같은 사람 판정이 이 값의 성질에 걸려 있다.
 */
class IdentityKeyHasherTest {

    private static final byte[] PEPPER = "pepper-A-0123456789012345678901234567".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OTHER_PEPPER = "pepper-B-0123456789012345678901234567".getBytes(StandardCharsets.UTF_8);
    private static final String CI = "abcDEF123+/xyz==";

    @Test
    @DisplayName("같은 CI 는 언제나 같은 키 — 무작위 salt 가 아니다. 그래야 다시 온 사람을 알아본다")
    void deterministic() {
        assertThat(IdentityKeyHasher.hash(PEPPER, CI)).isEqualTo(IdentityKeyHasher.hash(PEPPER, CI));
    }

    @Test
    @DisplayName("키에 CI 원본이 드러나지 않는다 — 64자 16진수")
    void doesNotRevealCi() {
        String key = IdentityKeyHasher.hash(PEPPER, CI);
        assertThat(key).hasSize(64).matches("[0-9a-f]{64}").doesNotContain(CI);
    }

    @Test
    @DisplayName("비밀값이 다르면 키가 다르다 — DB 만 새어서는 다른 곳의 CI 와 맞춰 볼 수 없다")
    void dependsOnPepper() {
        assertThat(IdentityKeyHasher.hash(PEPPER, CI)).isNotEqualTo(IdentityKeyHasher.hash(OTHER_PEPPER, CI));
    }

    @Test
    @DisplayName("CI 가 다르면 키가 다르다")
    void differentPeopleDiffer() {
        assertThat(IdentityKeyHasher.hash(PEPPER, CI)).isNotEqualTo(IdentityKeyHasher.hash(PEPPER, CI + "x"));
    }

    @Test
    @DisplayName("앞뒤 공백은 같은 CI 로 본다 — 공급자 응답의 개행 하나로 다른 사람이 되면 안 된다")
    void trimsWhitespace() {
        assertThat(IdentityKeyHasher.hash(PEPPER, " " + CI + "\n")).isEqualTo(IdentityKeyHasher.hash(PEPPER, CI));
    }

    @Test
    @DisplayName("CI 가 없으면 키를 만들지 않는다 — 빈 값끼리 같은 사람이 되면 안 된다")
    void blankCiIsRejected() {
        assertThatThrownBy(() -> IdentityKeyHasher.hash(PEPPER, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdentityKeyHasher.hash(PEPPER, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
