package com.hanseongge.domain.user.entity;

import java.util.List;

/**
 * 약관 종류. V8 {@code user_agreements.agreement_type} CHECK 제약과 값이 일치해야 한다.
 *
 * <p>필수/선택 구분이 여기 있는 이유: 화면과 서버가 각자 목록을 들고 있으면 언젠가
 * 어긋나고, 그때 빠지는 쪽은 대개 법적 필수 항목이다.
 */
public enum AgreementType {

    /** 이용약관 */
    TERMS("이용약관", true),
    /** 개인정보 수집·이용 동의 */
    PRIVACY("개인정보 수집·이용 동의", true),
    /** 만 14세 이상 확인 — 개인정보보호법상 만 14세 미만은 법정대리인 동의가 필요하다 */
    AGE_14("만 14세 이상입니다", true),
    /** 마케팅 정보 수신. 언제든 철회할 수 있다 */
    MARKETING("마케팅 정보 수신 동의", false),
    /** 제3자 제공 (배송사 등). 주문 시점에 별도로 받는다 */
    THIRD_PARTY("개인정보 제3자 제공 동의", false);

    private final String displayName;
    private final boolean required;

    AgreementType(String displayName, boolean required) {
        this.displayName = displayName;
        this.required = required;
    }

    public String displayName() {
        return displayName;
    }

    public boolean isRequired() {
        return required;
    }

    /** 가입을 완료하려면 반드시 동의해야 하는 약관. */
    public static List<AgreementType> requiredTypes() {
        return List.of(TERMS, PRIVACY, AGE_14);
    }
}
