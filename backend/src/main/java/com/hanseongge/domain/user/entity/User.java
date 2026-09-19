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
 * <h2>가입 경로는 둘, 본인확인은 같다</h2>
 * 자체(휴대폰+비밀번호)든 소셜이든 <b>휴대폰 본인확인을 거친다.</b> 예외는 없다 —
 * 한쪽만 면제하면 "본인확인을 안 한 회원"이라는 상태가 생기고, 그 예외가 주문·매입·
 * 이벤트 코드 전체로 번진다. 본인확인 공급자가 없는 동안에는 가입 자체를 받지 않는다.
 *
 * <h2>로그인 ID 는 휴대폰번호, 같은 사람 판정은 DI</h2>
 * 휴대폰번호는 사람이 아니라 회선에 붙는다 — 해지된 번호는 다른 사람에게 다시 나간다.
 * 그래서 "이미 가입한 사람인가"는 {@link #di} 로 판정하고, 번호는 어느 계정으로
 * 로그인하는지만 가리킨다. 둘이 어긋나면(다른 DI 가 이 번호로 본인확인) 번호가
 * 재할당된 것이고, 이 계정의 번호를 비운다 ({@link #releasePhone}).
 *
 * <h2>신원 정보는 회원이 고칠 수 없다</h2>
 * {@link #name}·{@link #birthDate}·{@link #gender}·{@link #foreigner}·{@link #phone}·{@link #di}
 * 는 본인확인 응답으로만 채워진다. 고칠 수 있으면 "확인된 값"이 아니라 "한 번 확인했다가
 * 지금은 모르는 값"이 된다. 바뀌었으면(개명·번호 변경) 본인확인을 다시 받아
 * {@link #refreshIdentity} 로 맞춘다.
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
     * 선택 항목 — 세금계산서·거래명세서를 받을 주소. 로그인 ID 가 아니다.
     *
     * <p>탈퇴하지 않은 회원 사이에서 {@code lower(email)} 이 UNIQUE 다 (V13). 조회는
     * 반드시 {@code lower()} 로 해야 그 인덱스를 탄다.
     */
    @Column(name = "email", length = 255)
    private String email;

    /** 본인확인기관이 준 실명. 회원이 고칠 수 없다. */
    @Column(name = "name", length = 100)
    private String name;

    /**
     * 로그인 ID. 본인확인기관이 준 휴대폰번호를 숫자만 남겨 둔다 ({@code 01012345678}).
     *
     * <p>탈퇴하지 않은 회원 사이에서 UNIQUE 다 (V21). 번호가 다른 사람에게 재할당되면
     * null 이 된다 — 그 회원은 소셜 로그인이나 본인확인으로 번호를 다시 받는다.
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

    private User(String email, boolean emailVerified, VerifiedIdentity identity, String carrier,
                 String passwordHash) {
        this.email = email;
        this.di = identity.di();
        this.passwordHash = passwordHash;

        OffsetDateTime now = OffsetDateTime.now();
        this.status = UserStatus.ACTIVE;
        this.emailVerifiedAt = email != null && emailVerified ? now : null;
        this.passwordChangedAt = passwordHash == null ? null : now;
        this.createdAt = now;
        applyIdentity(identity, carrier, now);
    }

    /**
     * 신규 가입.
     *
     * <p>이 생성자를 부르기 전에 <b>휴대폰 본인확인과 필수 약관 동의가 끝나 있어야 한다.</b>
     * 계정을 먼저 만들고 동의를 나중에 받으면 그 사이 개인정보를 동의 없이 보관한 상태가
     * 된다. 순서를 강제하는 곳은 {@code UserAccountService} 다.
     *
     * @param email         선택. 없으면 null
     * @param emailVerified 이메일 소유를 확인했는가 — 인증번호를 통과했거나 소셜 제공자가 준 값
     * @param passwordHash  소셜 전용 가입이면 null
     */
    public static User signUp(String email, boolean emailVerified, VerifiedIdentity identity,
                              String carrier, String passwordHash) {
        return new User(email, emailVerified, identity, carrier, passwordHash);
    }

    /**
     * 본인확인을 마친 회원인가.
     *
     * <p>V21 부터 활성 회원은 전부 true 다 ({@code chk_users_identity_required}). 관리자
     * 화면의 표시와 탈퇴 행 처리를 위해 남겨 둔다.
     */
    public boolean isIdentityVerified() {
        return di != null;
    }

    /**
     * 같은 사람이 다시 본인확인을 했을 때 최신 값으로 맞춘다.
     *
     * <p>이름(개명)·번호(번호 변경)·통신사는 바뀔 수 있다. 바뀐 값을 반영하지 않으면
     * 로그인 ID 가 이미 해지된 번호로 남고, 매입 대금의 예금주 대조가 옛 이름으로 된다.
     *
     * <p>DI 가 다르면 같은 사람이 아니므로 거부한다 — 호출자의 실수가 곧 계정 탈취다.
     */
    public void refreshIdentity(VerifiedIdentity identity, String carrier) {
        if (!this.di.equals(identity.di())) {
            throw new IllegalArgumentException("다른 사람의 본인확인 결과로 회원 정보를 바꿀 수 없습니다.");
        }
        applyIdentity(identity, carrier, OffsetDateTime.now());
    }

    private void applyIdentity(VerifiedIdentity identity, String carrier, OffsetDateTime now) {
        this.name = identity.name();
        this.phone = PhoneNumber.normalize(identity.phone());
        this.birthDate = identity.birthDate();
        this.gender = identity.gender();
        this.foreigner = identity.foreigner();
        this.carrier = carrier;
        this.phoneVerifiedAt = now;
        this.updatedAt = now;
    }

    /**
     * 번호가 다른 사람에게 재할당됐다 — 이 계정의 로그인 ID 를 비운다.
     *
     * <p>다른 DI 가 이 번호로 본인확인을 통과했다는 것은 통신사가 번호를 다른 사람에게
     * 넘겼다는 뜻이다. 번호를 그대로 두면 새 주인이 이 계정으로 SMS 로그인을 할 수 있다.
     * 이 계정의 주인은 소셜 로그인이나 본인확인(비밀번호 찾기)으로 새 번호를 받는다.
     */
    public void releasePhone() {
        this.phone = null;
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
