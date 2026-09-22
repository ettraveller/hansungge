package com.hanseongge.support;

import com.hanseongge.domain.user.entity.User;
import com.hanseongge.domain.user.entity.VerifiedIdentity;
import com.hanseongge.domain.user.identity.IdentityKeyHasher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 회원 테스트 픽스처.
 *
 * <p>본인인증을 받은 회원은 판정 키(CI 해시)를 가진다. 그 신원을 테스트마다 손으로 만들면
 * <b>CI 나 휴대폰을 고정해 두는 실수</b>가 나기 쉽고, 두 번째 회원을 만드는 순간 로그인 ID
 * UNIQUE 에 걸려 원인을 찾느라 시간을 쓴다.
 *
 * <p>그래서 기본은 매번 다른 사람({@link Person} — 다른 CI·다른 번호)을 준다. 같은 사람을
 * <b>일부러</b> 시험하는 테스트만 {@link #personWith} 로 같은 값을 지정한다.
 *
 * <p>픽스처가 만든 회원의 판정 키와 스텁 본인인증이 만든 키가 같은 사람에서 일치해야 하므로,
 * 계산에 쓰는 비밀값({@link #TEST_PEPPER})이 {@code application-test.yml} 과 같다.
 */
public final class UserTestFixtures {

    /** {@code application-test.yml} 의 {@code app.identity.hash-pepper} 와 같아야 한다. */
    public static final String TEST_PEPPER = "test-pepper-for-integration-tests-only-0000";

    private UserTestFixtures() {
    }

    /**
     * 본인인증 공급자가 돌려줄 사람 — CI 원본을 가진다. 회원에 저장되는 것은 {@link #identity()} 의 판정 키다.
     */
    public record Person(String ci, String name, LocalDate birthDate, String phone) {

        /** 이 사람이 본인인증을 마쳤을 때 회원에 남는 신원. */
        public VerifiedIdentity identity() {
            return new VerifiedIdentity(keyOf(ci), name, birthDate, phone);
        }

        public String identityKey() {
            return keyOf(ci);
        }
    }

    /** 매번 다른 사람(CI·휴대폰). 일반적인 회원 생성에 쓴다. */
    public static Person person(String name) {
        return personWith("CI-" + UUID.randomUUID(), name, randomPhone());
    }

    /** CI 를 지정한다 — 같은 사람이 다시 오는 상황. 번호는 새로 만든다. */
    public static Person personWithCi(String ci, String name) {
        return personWith(ci, name, randomPhone());
    }

    /** CI·번호를 지정한다 — 번호 재할당(다른 사람, 같은 번호)을 시험할 때 쓴다. */
    public static Person personWith(String ci, String name, String phone) {
        return new Person(ci, name, LocalDate.of(1990, 1, 1), phone);
    }

    /** 만 14세 미만. 가입 차단을 시험할 때 쓴다. */
    public static Person minor(LocalDate today) {
        return new Person("CI-" + UUID.randomUUID(), "미성년", today.minusYears(13), randomPhone());
    }

    /** 앱과 같은 방식으로 판정 키를 만든다. */
    public static String keyOf(String ci) {
        return IdentityKeyHasher.hash(TEST_PEPPER.getBytes(StandardCharsets.UTF_8), ci);
    }

    /** 010 + 8자리. 숫자만 — 저장 형식과 같다. */
    public static String randomPhone() {
        return "010" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    /** 소셜 전용 회원 — 본인인증·비밀번호·휴대폰이 없다. 소셜 연결 행은 호출자가 만든다. */
    public static User socialUser(String email, String name) {
        return User.signUpSocial(email, name);
    }

    /** 본인인증 + 비밀번호가 있는 자체 회원. 해시는 호출자가 만든다. */
    public static User passwordUser(String email, String name, String passwordHash) {
        return User.signUpVerified(email, email != null, person(name).identity(), "STUB", passwordHash);
    }

    /** 본인인증 + 비밀번호가 있는 자체 회원 — 사람을 지정한다(휴대폰 로그인·중복가입을 시험할 때). */
    public static User passwordUser(Person person, String passwordHash) {
        return User.signUpVerified(null, false, person.identity(), "STUB", passwordHash);
    }

    /**
     * 회원 관련 행을 전부 지운다.
     *
     * <p>테스트마다 이 순서를 손으로 쓰면 <b>새 테이블이 생길 때마다 여러 파일을 같이
     * 고쳐야 하고</b>, 한 곳을 빠뜨리면 그 테스트만 외래키 위반으로 이상하게 실패한다.
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
            jdbc.update("DELETE FROM verification_codes");
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
