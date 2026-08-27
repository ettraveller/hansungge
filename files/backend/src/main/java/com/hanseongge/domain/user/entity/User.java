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

import java.time.OffsetDateTime;

/**
 * 회원 ({@code users}).
 *
 * <p><b>비밀번호 컬럼이 없다.</b> 로그인은 소셜 인증만 지원한다 (개발계획 §7.1). 보관하지
 * 않는 비밀번호는 유출될 수도, 재사용될 수도 없다.
 *
 * <p>동일 이메일이라도 제공자가 다르면 <b>별도 계정</b>이다 (§7.2). 이메일로 자동 병합하면
 * "공격자가 피해자 이메일로 소셜 계정을 만들어 기존 계정을 흡수"하는 경로가 열린다.
 * 계정 통합은 마이페이지에서 본인이 로그인한 상태로만 한다.
 */
@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 소셜 제공자가 준 이메일. 인증 수단이 아니라 <b>연락처</b>다.
     *
     * <p>UNIQUE 가 아니다 — 같은 이메일로 구글·카카오 계정이 각각 존재할 수 있고,
     * 제공자가 이메일을 주지 않는 경우(카카오 선택 동의 거부)에는 null 이다.
     */
    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "name", length = 100)
    private String name;

    /** 앱 계층 암호화 대상 (설계서 §15). 주문 단계에서 입력받는다. */
    @Column(name = "phone", length = 30)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private User(String email, String name) {
        this.email = email;
        this.name = name;
        this.status = UserStatus.ACTIVE;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 신규 가입.
     *
     * <p>이 생성자를 부르기 전에 <b>필수 약관 동의가 끝나 있어야 한다.</b> 계정을 먼저
     * 만들고 동의를 나중에 받으면, 그 사이 개인정보를 동의 없이 보관한 상태가 된다.
     * 순서를 강제하는 곳은 {@code UserSignUpService} 다.
     */
    public static User signUp(String email, String name) {
        return new User(email, name);
    }

    public void updateProfile(String name, String phone) {
        this.name = name;
        this.phone = phone;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 소셜 제공자의 최신 이름·이메일을 반영한다. 사용자가 직접 고친 값은 덮지 않는다. */
    public void syncFromProvider(String providerEmail, String providerName) {
        boolean changed = false;
        if (this.email == null && providerEmail != null) {
            this.email = providerEmail;
            changed = true;
        }
        if (this.name == null && providerName != null) {
            this.name = providerName;
            changed = true;
        }
        if (changed) {
            this.updatedAt = OffsetDateTime.now();
        }
    }

    /**
     * 탈퇴.
     *
     * <p>행을 지우지 않는다. 주문·결제 이력이 회원을 참조하고 있어 삭제하면 과거 거래의
     * 주체가 사라진다. 전자상거래법상 거래 기록은 5년 보존 의무가 있다.
     * 개인정보 파기는 별도 배치가 보존기간 경과 후 처리한다 (STEP 18).
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
