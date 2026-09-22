package com.hanseongge.domain.user.entity;

import com.hanseongge.support.UserTestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("회원 로그인 잠금 — 30분이 지나면 스스로 풀린다")
class UserLockTest {

    private static final int MAX = 5;

    private User lockedUser() {
        User user = UserTestFixtures.passwordUser(UserTestFixtures.person("홍길동"), "$2a$12$hash");
        for (int i = 0; i < MAX; i++) {
            user.recordLoginFailure(MAX);
        }
        return user;
    }

    @Test
    @DisplayName("5번 틀리면 잠긴다")
    void locksAtMax() {
        User user = lockedUser();
        assertThat(user.isLocked(OffsetDateTime.now())).isTrue();
        assertThat(user.getLoginFailCount()).isEqualTo(MAX);
    }

    @Test
    @DisplayName("30분이 지나면 잠금이 풀린다")
    void unlocksAfterDuration() {
        User user = lockedUser();
        assertThat(user.isLocked(OffsetDateTime.now().plus(User.LOCK_DURATION).plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("풀린 뒤 한 번 틀려도 곧바로 다시 잠기지 않는다 — 실패 횟수를 새로 센다")
    void expiredLockStartsCountingAgain() {
        User user = lockedUser();
        ReflectionTestUtils.setField(user, "lockedAt", OffsetDateTime.now().minusMinutes(31));

        boolean lockedAgain = user.recordLoginFailure(MAX);

        assertThat(lockedAgain).isFalse();
        assertThat(user.getLoginFailCount()).isEqualTo(1);
        assertThat(user.getLockedAt()).isNull();
        assertThat(user.isLocked(OffsetDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("풀린 뒤에도 다시 5번 틀리면 다시 잠긴다")
    void relocksAfterFiveMore() {
        User user = lockedUser();
        ReflectionTestUtils.setField(user, "lockedAt", OffsetDateTime.now().minusMinutes(31));

        for (int i = 0; i < MAX - 1; i++) {
            assertThat(user.recordLoginFailure(MAX)).isFalse();
        }
        assertThat(user.recordLoginFailure(MAX)).isTrue();
        assertThat(user.isLocked(OffsetDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("잠겨 있는 동안의 실패는 카운터를 되돌리지 않는다")
    void stillLockedKeepsCounting() {
        User user = lockedUser();
        user.recordLoginFailure(MAX);
        assertThat(user.getLoginFailCount()).isEqualTo(MAX + 1);
    }
}
