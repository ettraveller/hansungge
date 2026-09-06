package com.hanseongge.support;

import com.hanseongge.domain.user.entity.Gender;
import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.entity.VerifiedIdentity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 회원 테스트 픽스처.
 *
 * <p>자체 가입·본인확인이 들어오면서 {@code User.signUp} 이 검증된 신원을 요구한다.
 * 그 신원을 테스트마다 손으로 만들면 <b>DI 를 고정해 두는 실수</b>가 나기 쉽고, 그러면
 * 두 번째 회원을 만드는 순간 중복가입 UNIQUE 에 걸려 원인을 찾느라 시간을 쓴다.
 *
 * <p>그래서 기본은 매번 다른 DI 를 준다. 중복가입을 <b>일부러</b> 시험하는 테스트만
 * {@link #identityWithDi} 로 같은 값을 지정한다.
 */
public final class UserTestFixtures {

    private UserTestFixtures() {
    }

    /** 매번 다른 신원. 일반적인 회원 생성에 쓴다. */
    public static VerifiedIdentity identity(String name) {
        return identityWithDi("DI-" + UUID.randomUUID(), name);
    }

    /** DI 를 지정한다 — 중복가입·계정 병합을 시험할 때만 쓴다. */
    public static VerifiedIdentity identityWithDi(String di, String name) {
        return new VerifiedIdentity(di, name, LocalDate.of(1990, 1, 1), Gender.MALE, false,
                "010-0000-0000");
    }

    /** 만 14세 미만. 가입 차단을 시험할 때 쓴다. */
    public static VerifiedIdentity minorIdentity(LocalDate today) {
        return new VerifiedIdentity("DI-" + UUID.randomUUID(), "미성년",
                today.minusYears(13), Gender.FEMALE, false, "010-0000-0001");
    }

    /** 소셜 전용 회원 — 비밀번호가 없다. */
    public static User socialUser(String email, String name) {
        return User.signUp(email, identity(name), null);
    }

    /** 비밀번호가 있는 회원. 해시는 호출자가 만든다. */
    public static User passwordUser(String email, String name, String passwordHash) {
        return User.signUp(email, identity(name), passwordHash);
    }

    /**
     * 회원 관련 행을 전부 지운다.
     *
     * <p>테스트마다 이 순서를 손으로 쓰면 <b>새 테이블이 생길 때마다 여러 파일을 같이
     * 고쳐야 하고</b>, 한 곳을 빠뜨리면 그 테스트만 외래키 위반으로 이상하게 실패한다.
     * 실제로 {@code identity_verifications} 가 생겼을 때 그 일이 있었다.
     *
     * <p>삭제 순서는 참조의 역순이다. 그리고 append-only 트리거가 걸린 두 테이블은
     * 잠시 내렸다 올린다 — 운영에서는 절대 하지 않는 일이라 테스트에만 둔다.
     */
    public static void deleteAll(JdbcTemplate jdbc) {
        jdbc.execute("ALTER TABLE user_agreements DISABLE TRIGGER trg_user_agreement_guard");
        jdbc.execute("ALTER TABLE identity_verifications DISABLE TRIGGER trg_identity_verification_guard");
        try {
            jdbc.update("DELETE FROM user_agreements");
            jdbc.update("DELETE FROM identity_verifications");
            jdbc.update("DELETE FROM email_verifications");
            jdbc.update("DELETE FROM refresh_tokens");
            jdbc.update("DELETE FROM social_accounts");
            jdbc.update("DELETE FROM user_roles");
            jdbc.update("DELETE FROM users");
        } finally {
            jdbc.execute("ALTER TABLE identity_verifications ENABLE TRIGGER trg_identity_verification_guard");
            jdbc.execute("ALTER TABLE user_agreements ENABLE TRIGGER trg_user_agreement_guard");
        }
    }
}
