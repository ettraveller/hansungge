package com.hanseongge.domain.user.entity;

import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import com.hanseongge.global.util.PhoneNumber;
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
 * <h2>가입 경로는 둘, 본인인증은 자체 가입만</h2>
 * <ul>
 *   <li><b>자체</b> — 간편인증(PASS·토스·은행 등)을 거친다. 실명·생년월일·휴대폰·판정 키가 채워지고,
 *       휴대폰번호가 로그인 ID 가 된다
 *   <li><b>소셜</b> — 본인인증 없이 가입한다 (2026-09-21 결정). 판정 키·휴대폰·비밀번호가 없고,
 *       이름은 제공자가 준 값(검증 안 됨)이다. 매입·정산계좌처럼 돈이 나가는 일 앞에서
 *       본인인증({@link #applyVerifiedIdentity})을 받으면 그때 신원이 채워진다
 * </ul>
 * 본인인증을 마쳤는지는 {@link #identityVerifiedAt} 으로 가린다.
 *
 * <h2>로그인 ID 는 휴대폰번호, 같은 사람 판정은 판정 키</h2>
 * 휴대폰번호는 사람이 아니라 회선에 붙는다 — 해지된 번호는 다른 사람에게 다시 나간다.
 * 그래서 "이미 가입한 사람인가"는 {@link #identityKey}(CI 해시)로 판정하고, 번호는 어느 계정으로
 * 로그인하는지만 가리킨다. 둘이 어긋나면(다른 사람이 이 번호로 본인인증) 번호가
 * 재할당된 것이고, 이 계정의 번호를 비운다 ({@link #releasePhone}).
 *
 * <p>판정 키는 UNIQUE 가 아니다 — 소셜 가입자가 나중에 본인인증하면 같은 사람의 자체 계정과
 * 같은 키를 가질 수 있다(중복가입 허용).
 *
 * <h2>본인인증 값은 회원이 고칠 수 없다</h2>
 * 본인인증 뒤의 {@link #name}·{@link #birthDate}·{@link #phone}·{@link #identityKey} 는
 * 본인인증 응답으로만 바뀐다. 개명·번호 변경은 본인인증을 다시 받아 맞춘다.
 *
 * <h2>비밀번호는 있을 수도 없을 수도 있다</h2>
 * 소셜로만 가입한 회원은 {@link #passwordHash} 가 null 이다. 마이페이지에서 비밀번호를 정할 수
 * 있지만, 휴대폰 로그인에는 본인인증으로 받은 휴대폰번호도 있어야 한다.
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
     * 선택 항목 — 세금계산서·거래명세서를 받을 주소. 로그인 ID 가 아니다.
     *
     * <p>탈퇴하지 않은 회원 사이에서 {@code lower(email)} 이 UNIQUE 다 (V13). 조회는
     * 반드시 {@code lower()} 로 해야 그 인덱스를 탄다.
     */
    @Column(name = "email", length = 255)
    private String email;

    /**
     * 이름. 본인인증을 마친 회원은 실명(회원이 고칠 수 없다). 본인인증 전 소셜 회원은 제공자가 준
     * 표시 이름이거나 null — <b>검증되지 않은 값</b>이라 예금주 대조 등에 쓰면 안 된다.
     */
    @Column(name = "name", length = 100)
    private String name;

    /**
     * 로그인 ID. 본인인증으로 받은 휴대폰번호를 숫자만 남겨 둔다 ({@code 01012345678}).
     * 본인인증 전 소셜 회원은 null 이다.
     *
     * <p>탈퇴하지 않은 회원 사이에서 UNIQUE 다 (V21). 번호가 다른 사람에게 재할당되면
     * null 이 된다 — 그 회원은 소셜 로그인이나 본인인증으로 번호를 다시 받는다.
     *
     * <p>⚠️ 설계서 §15 는 앱 계층 AES-256 암호화를 요구하는데 아직 구현되지 않았다
     * (TODO F-4). 이제 로그인 조회 키라서, 암호화하려면 결정적 해시 컬럼을 따로 둬야 한다.
     */
    @Column(name = "phone", length = 30)
    private String phone;

    /** 본인확인 응답의 통신사. 마지막 본인확인 기준이다. */
    @Column(name = "carrier", length = 20)
    private String carrier;

    /** BCrypt cost 12. 소셜로만 가입한 회원은 null 이다. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    /** 본인인증 응답. 만 14세 판정의 근거다. 본인인증 전 소셜 회원은 null. */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /**
     * 같은 사람 판정 키 = HMAC-SHA256(pepper, CI). 본인인증 전 소셜 회원은 null.
     *
     * <p><b>CI 원본은 저장하지 않는다.</b> 간편인증의 CI 는 공급자가 "비교검증 후 파기"를 권고하는
     * 값이고, 정보통신망법 §23조의5·6 으로 CI 이용기관에 직접 의무가 있다. 비교에는 해시로 충분하다.
     *
     * <p>UNIQUE 가 아니다 (V23) — 중복 안내는 자체 가입 때 조회로만 한다.
     */
    @Column(name = "identity_key", length = 64)
    private String identityKey;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    /** 마지막 본인인증 시각. null 이면 본인인증을 받지 않은 회원(소셜 가입자)이다. */
    @Column(name = "identity_verified_at")
    private OffsetDateTime identityVerifiedAt;

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

    private User(String email, boolean emailVerified, String name, String passwordHash) {
        this.email = email;
        this.name = name;
        this.passwordHash = passwordHash;

        OffsetDateTime now = OffsetDateTime.now();
        this.status = UserStatus.ACTIVE;
        this.emailVerifiedAt = email != null && emailVerified ? now : null;
        this.passwordChangedAt = passwordHash == null ? null : now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 자체 가입 — 본인인증을 마친 사람.
     *
     * <p>이 메서드를 부르기 전에 <b>본인인증과 필수 약관 동의가 끝나 있어야 한다.</b>
     * 계정을 먼저 만들고 동의를 나중에 받으면 그 사이 개인정보를 동의 없이 보관한 상태가
     * 된다. 순서를 강제하는 곳은 {@code UserAccountService} 다.
     *
     * @param email         선택. 없으면 null
     * @param emailVerified 이메일 인증번호를 통과했는가
     */
    public static User signUpVerified(String email, boolean emailVerified, VerifiedIdentity identity,
                                      String carrier, String passwordHash) {
        if (passwordHash == null) {
            throw new IllegalArgumentException("자체 가입에는 비밀번호가 있어야 합니다.");
        }
        User user = new User(email, emailVerified, identity.name(), passwordHash);
        user.applyIdentity(identity, carrier, user.createdAt);
        return user;
    }

    /**
     * 소셜 가입 — 본인인증 없이.
     *
     * <p>판정 키·휴대폰·생년월일·비밀번호가 모두 없다. 이름은 제공자가 준 표시 이름(검증 안 됨).
     * 만 14세 확인은 약관 동의({@code AGE_14})로 갈음한다.
     */
    public static User signUpSocial(String email, String providerName) {
        return new User(email, email != null, providerName, null);
    }

    /** 본인인증을 받은 적이 있는 회원인가. 소셜 가입자는 본인인증 전까지 false. */
    public boolean isIdentityVerified() {
        return identityVerifiedAt != null;
    }

    /**
     * 본인인증 결과를 이 회원에 반영한다 — 다시 인증한 자체 회원, 또는 처음 인증하는 소셜 회원.
     *
     * <p>이름(개명)·번호(번호 변경)·통신사는 바뀔 수 있다. 바뀐 값을 반영하지 않으면
     * 로그인 ID 가 이미 해지된 번호로 남고, 매입 대금의 예금주 대조가 옛 이름으로 된다.
     *
     * <p>이미 판정 키가 있는데 다른 키가 오면 <b>다른 사람</b>이므로 거부한다 — 호출자의 실수가 곧
     * 계정 탈취다. 키가 없던 회원(소셜)은 이번 인증으로 처음 채워진다.
     */
    public void applyVerifiedIdentity(VerifiedIdentity identity, String carrier) {
        requireSamePerson(identity);
        applyIdentity(identity, carrier, OffsetDateTime.now());
    }

    /**
     * 본인인증 결과를 반영하되 <b>로그인 번호는 건드리지 않는다</b>.
     *
     * <p>같은 사람의 다른 계정(자체 가입)이 이미 그 번호로 로그인하고 있을 때 쓴다. 번호를 이쪽으로
     * 옮기면 그 사람이 쓰던 휴대폰 로그인이 조용히 끊긴다.
     */
    public void applyVerifiedIdentityKeepingPhone(VerifiedIdentity identity, String carrier) {
        requireSamePerson(identity);
        String kept = this.phone;
        applyIdentity(identity, carrier, OffsetDateTime.now());
        this.phone = kept;
    }

    private void requireSamePerson(VerifiedIdentity identity) {
        if (this.identityKey != null && !this.identityKey.equals(identity.identityKey())) {
            throw new BusinessException(ErrorCode.IDENTITY_MISMATCH);
        }
    }

    private void applyIdentity(VerifiedIdentity identity, String carrier, OffsetDateTime now) {
        this.identityKey = identity.identityKey();
        this.name = identity.name();
        this.phone = PhoneNumber.normalize(identity.phone());
        this.birthDate = identity.birthDate();
        this.carrier = carrier;
        this.identityVerifiedAt = now;
        this.updatedAt = now;
    }

    /**
     * 휴대폰번호 + 비밀번호로 로그인할 수 있는 계정인가.
     *
     * <p>비밀번호만 있고 번호가 없으면(본인인증 전 소셜 회원, 번호가 재할당된 회원) 로그인할 ID 가 없다.
     */
    public boolean canPasswordLogin() {
        return passwordHash != null && phone != null;
    }

    /**
     * 번호가 다른 사람에게 재할당됐다 — 이 계정의 로그인 ID 를 비운다.
     *
     * <p>다른 사람이 이 번호로 본인인증을 통과했다는 것은 통신사가 번호를 다른 사람에게
     * 넘겼다는 뜻이다. 번호를 그대로 두면 새 주인이 이 계정으로 SMS 로그인을 할 수 있다.
     * 이 계정의 주인은 소셜 로그인이나 본인인증(비밀번호 찾기)으로 새 번호를 받는다.
     */
    public void releasePhone() {
        this.phone = null;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 소셜 제공자의 이메일로 빈 자리를 채운다 (카카오에서 이메일 동의를 거부했다가 나중에 허용한 경우).
     *
     * <p>이름은 건드리지 않는다 — 본인인증을 마친 회원이면 실명이 이미 있다.
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
     * <p>같은 사람이 자체 로그인을 원할 때 계정을 새로 만들지 않기 위한 경로다.
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
        OffsetDateTime now = OffsetDateTime.now();

        // 잠금 기간이 지나 풀린 계정이면 지난 실패를 지우고 새로 센다. 안 지우면
        // 카운터가 이미 임계치에 있어서, 풀린 뒤 한 번만 틀려도 곧바로 다시 30분 잠긴다
        // — 스스로 풀린다는 약속이 사실상 "한 번의 기회"로 줄어든다.
        if (this.lockedAt != null && !isLocked(now)) {
            this.loginFailCount = 0;
            this.lockedAt = null;
        }

        this.loginFailCount += 1;
        this.updatedAt = now;
        if (this.loginFailCount >= maxFailures) {
            this.lockedAt = now;
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
     * <p>이메일·휴대폰의 UNIQUE 인덱스와 판정 키 조회는 탈퇴한 행을 제외하므로, 같은 사람이
     * 나중에 다시 가입할 수 있다.
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
