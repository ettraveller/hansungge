package com.hanseongge.domain.user.entity;

import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 회원 ({@code users}).
 *
 * <h2>가입 경로는 둘, 인증은 같다</h2>
 * 자체(이메일+비밀번호)든 소셜이든 <b>이메일 인증과 휴대폰 본인확인을 모두 거친다.</b>
 * 한쪽만 면제하면 "본인확인을 안 한 회원"이라는 상태가 생기고, 그 예외가 주문·매입·
 * 이벤트 코드 전체로 번진다.
 *
 * <p>⚠️ <b>단 하나의 예외가 한시적으로 있다.</b> 본인확인 공급자 계약(TODO E-4)이
 * 끝나기 전까지는 소셜 가입이 {@link #signUpWithoutIdentity} 로 이루어진다. 계약을
 * 기다리느라 이미 되던 소셜 로그인까지 막을 수는 없기 때문이다. 그렇게 만들어진
 * 회원은 {@link #isIdentityVerified()} 가 false 다.
 *
 * <h2>신원 정보는 회원이 고칠 수 없다</h2>
 * {@link #name}·{@link #birthDate}·{@link #gender}·{@link #foreigner}·{@link #phone}·{@link #di}
 * 는 본인확인 응답으로만 채워진다. 고칠 수 있으면 "확인된 값"이 아니라 "한 번 확인했다가
 * 지금은 모르는 값"이 된다. 그래서 {@link #updateProfile} 은 이 값들을 건드리지 않는다.
 *
 * <h2>비밀번호는 있을 수도 없을 수도 있다</h2>
 * 소셜로만 가입한 회원은 {@link #passwordHash} 가 null 이다. 나중에 같은 사람이 자체
 * 로그인을 원하면 {@link #attachPassword} 로 붙인다 — 계정을 새로 만들지 않는다.
 */
@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    /**
     * 로그인 실패로 잠기는 시간.
     *
     * <p>관리자({@code AdminUser})는 임계치에 도달하면 <b>상태가 LOCKED 로 바뀌어</b> 다른
     * 관리자가 풀어줄 때까지 잠긴다. 회원에게 같은 방식을 쓰지 않는 이유는 푸는 사람이
     * 다르기 때문이다 — 관리자는 소수라 사람이 풀어주면 되지만, 고객은 고객센터에
     * 전화해야 한다. 무차별 대입은 30분이면 충분히 무의미해지고, 잊어버린 본인은
     * 기다리면 스스로 풀린다.
     */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 로그인 아이디이자 연락처.
     *
     * <p>탈퇴하지 않은 회원 사이에서 {@code lower(email)} 이 UNIQUE 다 (V13). 탈퇴한
     * 행까지 묶지 않는 이유는, 그러면 탈퇴한 본인조차 같은 주소로 다시 가입할 수 없기
     * 때문이다. 조회는 반드시 {@code lower()} 로 해야 그 인덱스를 탄다.
     */
    @Column(name = "email", length = 255)
    private String email;

    /** 본인확인기관이 준 실명. 회원이 고칠 수 없다. */
    @Column(name = "name", length = 100)
    private String name;

    /**
     * 본인확인기관이 준 휴대폰 번호. 회원이 고칠 수 없다.
     *
     * <p>⚠️ 설계서 §15 는 앱 계층 AES-256 암호화를 요구하는데 아직 구현되지 않았다.
     * 본인확인 도입으로 이 값이 "선택 입력값"에서 "검증된 필수 식별자"로 승격됐으므로
     * 더 미룰 수 없다.
     */
    @Column(name = "phone", length = 30)
    private String phone;

    /** BCrypt cost 12. 소셜로만 가입한 회원은 null 이다. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /** 본인확인 응답. 만 14세 판정의 근거다. */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "foreigner")
    private Boolean foreigner;

    /**
     * 본인확인기관이 발급한 중복가입확인정보.
     *
     * <p>같은 사람이 두 계정을 가질 수 없게 하는 근거다. 탈퇴하지 않은 회원 사이에서
     * UNIQUE 다 (V13).
     *
     * <p><b>CI 는 저장하지 않는다.</b> 2024-08-14 시행 정보통신망법 §23조의5·6 으로 CI
     * 이용기관에 직접 의무와 형벌이 생겼고(목적 외 사용 5년 이하 징역), 중복가입 방지에는
     * 사이트별 고유값인 DI 로 충분하다.
     */
    @Column(name = "di", length = 88)
    private String di;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "phone_verified_at")
    private OffsetDateTime phoneVerifiedAt;

    @Column(name = "login_fail_count", nullable = false)
    private int loginFailCount;

    @Column(name = "locked_at")
    private OffsetDateTime lockedAt;

    @Column(name = "password_changed_at")
    private OffsetDateTime passwordChangedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private User(String email, VerifiedIdentity identity, String passwordHash) {
        this.email = email;
        this.name = identity.name();
        this.phone = identity.phone();
        this.birthDate = identity.birthDate();
        this.gender = identity.gender();
        this.foreigner = identity.foreigner();
        this.di = identity.di();
        this.passwordHash = passwordHash;

        OffsetDateTime now = OffsetDateTime.now();
        this.status = UserStatus.ACTIVE;
        this.emailVerifiedAt = now;
        this.phoneVerifiedAt = now;
        this.passwordChangedAt = passwordHash == null ? null : now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 신규 가입.
     *
     * <p>이 생성자를 부르기 전에 <b>이메일 인증·휴대폰 본인확인·필수 약관 동의가 모두
     * 끝나 있어야 한다.</b> 계정을 먼저 만들고 동의를 나중에 받으면 그 사이 개인정보를
     * 동의 없이 보관한 상태가 된다. 순서를 강제하는 곳은 {@code UserAccountService} 다.
     *
     * @param passwordHash 소셜 전용 가입이면 null
     */
    public static User signUp(String email, VerifiedIdentity identity, String passwordHash) {
        return new User(email, identity, passwordHash);
    }

    /**
     * 본인확인 없이 만드는 소셜 회원 — <b>한시적이다.</b>
     *
     * <p>휴대폰 본인확인 공급자 계약(TODO E-4)이 끝나기 전까지만 쓰인다. 그 전에도
     * 소셜 로그인은 열어 두어야 하기 때문이다 — 계약을 기다리느라 <b>이미 되던
     * 로그인까지 막는 것</b>은 더 나쁘다.
     *
     * <p>이렇게 만든 회원은 {@link #di} 와 {@link #phoneVerifiedAt} 이 null 이다.
     * 공급자가 붙은 뒤에는 이 경로로 새 회원이 생기지 않고, 남아 있는 회원에게는
     * 거래 시점에 본인확인을 요구해야 한다 (TODO A-11).
     */
    public static User signUpWithoutIdentity(String email, String name) {
        User user = new User();
        user.email = email;
        user.name = name;
        user.status = UserStatus.ACTIVE;
        OffsetDateTime now = OffsetDateTime.now();
        user.emailVerifiedAt = now;
        user.createdAt = now;
        user.updatedAt = now;
        return user;
    }

    /** 본인확인을 마친 회원인가. 계약 전에 가입한 회원은 false 다. */
    public boolean isIdentityVerified() {
        return di != null;
    }

    /**
     * 회원이 직접 고칠 수 있는 값.
     *
     * <p>이름·휴대폰이 여기 없는 것은 누락이 아니다. 본인확인으로 확정된 값이라
     * 고칠 수 있으면 안 된다.
     */
    public void updateProfile(String name) {
        this.name = name;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 소셜 제공자의 이름·이메일로 빈 자리를 채운다.
     *
     * <p>이름은 이제 본인확인이 채우므로 비어 있을 일이 없고, 이메일만 실제로 쓰인다
     * (카카오에서 이메일 동의를 거부했다가 나중에 허용한 경우).
     */
    public void syncFromProvider(String providerEmail) {
        if (this.email == null && providerEmail != null) {
            this.email = providerEmail;
            this.updatedAt = OffsetDateTime.now();
        }
    }

    /**
     * 소셜로만 가입한 회원에게 비밀번호를 붙인다.
     *
     * <p>같은 사람(같은 DI)이 자체 로그인을 원할 때 계정을 새로 만들지 않기 위한 경로다.
     * 이미 비밀번호가 있으면 여기로 오면 안 된다 — 그건 재설정이고, 본인확인이나
     * 현재 비밀번호 확인을 거쳐야 한다.
     */
    public void attachPassword(String passwordHash) {
        if (this.passwordHash != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 비밀번호가 설정된 계정입니다.");
        }
        this.passwordHash = passwordHash;
        this.passwordChangedAt = OffsetDateTime.now();
        this.updatedAt = this.passwordChangedAt;
    }

    /** 비밀번호 변경·재설정. 잠금과 실패 횟수도 함께 푼다 — 본인임을 확인한 뒤이므로. */
    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = OffsetDateTime.now();
        this.loginFailCount = 0;
        this.lockedAt = null;
        this.updatedAt = this.passwordChangedAt;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    /** 로그인 성공. 실패 흔적을 지운다. */
    public void recordLogin() {
        this.loginFailCount = 0;
        this.lockedAt = null;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 로그인 실패.
     *
     * <p>⚠️ 이 메서드를 부른 트랜잭션 안에서 예외를 던지면 <b>증가한 실패 횟수가 함께
     * 롤백되어 아무리 시도해도 계정이 잠기지 않는다.</b> 호출자는 결과를 값으로 받아
     * 트랜잭션 밖에서 예외로 바꿔야 한다 ({@code AdminLoginAttemptService} 와 같은 이유).
     *
     * @return 이번 실패로 잠겼으면 true
     */
    public boolean recordLoginFailure(int maxFailures) {
        this.loginFailCount += 1;
        this.updatedAt = OffsetDateTime.now();
        if (this.loginFailCount >= maxFailures) {
            this.lockedAt = this.updatedAt;
            return true;
        }
        return false;
    }

    /** 잠금은 {@link #LOCK_DURATION} 이 지나면 스스로 풀린다. */
    public boolean isLocked(OffsetDateTime now) {
        return lockedAt != null && lockedAt.plus(LOCK_DURATION).isAfter(now);
    }

    /**
     * 관리자가 잠금을 즉시 푼다.
     *
     * <p>{@link #LOCK_DURATION} 이 지나면 어차피 풀리는데도 이 경로가 필요한 이유는,
     * <b>고객이 지금 전화를 걸어 왔기 때문</b>이다. "30분 뒤에 다시 해보세요"는 답이지만
     * 좋은 답은 아니고, 본인 확인이 끝난 상황이라면 기다릴 이유가 없다.
     *
     * <p>실패 횟수도 함께 0 으로 되돌린다. 잠금 시각만 지우면 다음 실패 한 번에 바로
     * 다시 잠긴다 — 푼 것처럼 보이지만 풀리지 않은 상태다.
     */
    public void unlock() {
        this.loginFailCount = 0;
        this.lockedAt = null;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 탈퇴.
     *
     * <p>행을 지우지 않는다. 주문·결제 이력이 회원을 참조하고 있어 삭제하면 과거 거래의
     * 주체가 사라진다. 전자상거래법상 거래 기록은 5년 보존 의무가 있다.
     * 개인정보 파기는 별도 배치가 보존기간 경과 후 처리한다 (STEP 18).
     *
     * <p>이메일과 DI 의 UNIQUE 인덱스는 탈퇴한 행을 제외하므로, 같은 사람이 나중에 다시
     * 가입할 수 있다.
     */
    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
        this.updatedAt = OffsetDateTime.now();
    }

    public void changeStatus(UserStatus status) {
        this.status = status;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 로그인·API 사용 가능 상태인지 확인하고, 아니면 사유에 맞는 예외를 던진다.
     *
     * <p>탈퇴와 정지를 한 코드로 묶지 않는다 — 탈퇴는 본인이 한 일이고 정지는 우리가 한
     * 일이라, 사용자가 다음에 무엇을 해야 하는지가 다르다.
     */
    public void requireSignInAllowed() {
        switch (status) {
            case ACTIVE -> { }
            case WITHDRAWN -> throw new BusinessException(ErrorCode.USER_WITHDRAWN);
            case BLOCKED -> throw new BusinessException(ErrorCode.USER_BLOCKED);
            case DORMANT -> throw new BusinessException(ErrorCode.USER_BLOCKED,
                    "휴면 계정입니다. 본인 확인 후 이용할 수 있습니다.");
        }
    }
}
