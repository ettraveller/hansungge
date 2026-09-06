package com.hanseongge.domain.user.entity;

import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;

import java.time.LocalDate;
import java.time.Period;

/**
 * 본인확인으로 확정된 신원.
 *
 * <p>본인확인기관이 돌려준 값 중 <b>회원에 남기는 것만</b> 모았다. 공급자 거래번호나
 * 통신사처럼 이력에만 필요한 값은 여기 없다.
 *
 * <p><b>CI 는 없다.</b> 필드가 있으면 언젠가 채워진다 — 2024-08-14 시행 정보통신망법
 * §23조의5·6 으로 CI 이용기관에 직접 의무와 형벌이 생겼다.
 *
 * @param di        중복가입확인정보. 사이트별 고유값
 * @param name      실명
 * @param birthDate 생년월일. 만 14세 판정의 근거
 * @param gender    성별
 * @param foreigner 외국인 여부
 * @param phone     휴대폰 번호
 */
public record VerifiedIdentity(
        String di,
        String name,
        LocalDate birthDate,
        Gender gender,
        boolean foreigner,
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
        if (di == null || di.isBlank()) {
            throw new IllegalArgumentException("본인확인 응답에 DI 가 없습니다.");
        }
        if (birthDate == null) {
            throw new IllegalArgumentException("본인확인 응답에 생년월일이 없습니다.");
        }
    }

    /**
     * 만 나이가 하한을 넘는지 확인하고, 아니면 예외를 던진다.
     *
     * <p>이 판정을 화면의 체크박스("만 14세 이상입니다")에 맡기지 않는다. 스스로 신고하는
     * 값은 검증이 아니다 — 본인확인이 생년월일을 주는데 그것을 두고 자기신고를 믿을 이유가
     * 없다. {@code AGREEMENT_AGE_14} 동의는 그대로 받되, 실제 판정은 여기서 한다.
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
}
