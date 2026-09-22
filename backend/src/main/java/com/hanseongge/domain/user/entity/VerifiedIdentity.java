package com.hanseongge.domain.user.entity;

import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;

import java.time.LocalDate;
import java.time.Period;

/**
 * 본인인증(간편인증)으로 확정된 신원.
 *
 * <p>본인인증 응답 중 <b>회원에 남기는 것만</b> 모았다. 공급자 거래번호나 통신사처럼
 * 이력에만 필요한 값은 여기 없다.
 *
 * <p><b>CI 원본이 없다.</b> 같은 사람 판정에는 {@link #identityKey} — CI 를 서버 비밀값으로
 * 해시한 값 — 만 쓴다. CI 는 응답을 읽은 직후 해시되고 버려진다 ({@code IdentityKeyHasher}).
 *
 * @param identityKey 같은 사람 판정 키 = HMAC-SHA256(pepper, CI)
 * @param name        실명
 * @param birthDate   생년월일. 만 14세 판정의 근거
 * @param phone       휴대폰 번호
 */
public record VerifiedIdentity(
        String identityKey,
        String name,
        LocalDate birthDate,
        String phone) {

    /**
     * 가입 하한 나이.
     *
     * <p>개인정보보호법 §22조의2 — 만 14세 미만은 법정대리인 동의가 있어야 개인정보를
     * 수집할 수 있다. 그 동의를 받는 절차를 만들지 않았으므로 <b>가입 자체를 막는다.</b>
     * 절차 없이 받아 두면 그 시점부터 위법 상태다.
     */
    public static final int MINIMUM_AGE = 14;

    public VerifiedIdentity {
        if (identityKey == null || identityKey.isBlank()) {
            throw new IllegalArgumentException("본인인증 결과에 판정 키가 없습니다.");
        }
        if (birthDate == null) {
            throw new IllegalArgumentException("본인인증 응답에 생년월일이 없습니다.");
        }
    }

    /**
     * 만 나이가 하한을 넘는지 확인하고, 아니면 예외를 던진다.
     *
     * <p>본인인증이 생년월일을 주는데 화면의 체크박스("만 14세 이상입니다")만 믿을 이유가 없다.
     * {@code AGE_14} 동의는 그대로 받되, 본인인증을 거치는 경로에서는 실제 판정을 여기서 한다.
     * (소셜 가입은 본인인증이 없어 동의로 갈음한다.)
     *
     * <p>기준 시각을 인자로 받는 이유는 테스트 때문이다. {@code LocalDate.now()} 를 안에서
     * 부르면 "오늘 만 14세가 되는 사람"을 검증할 수 없다.
     */
    public void requireMinimumAge(LocalDate today) {
        int age = Period.between(birthDate, today).getYears();
        if (age < MINIMUM_AGE) {
            throw new BusinessException(ErrorCode.UNDER_MINIMUM_AGE);
        }
    }

    /** 로그에 실명·번호·키가 찍히지 않게 한다. record 기본 toString 은 전부 찍는다. */
    @Override
    public String toString() {
        return "VerifiedIdentity[masked]";
    }
}
