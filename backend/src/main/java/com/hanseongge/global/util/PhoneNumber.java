package com.hanseongge.global.util;

import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;

import java.util.regex.Pattern;

/**
 * 휴대폰번호 — 로그인 ID 이므로 <b>한 가지 모양</b>으로만 다룬다.
 *
 * <h2>왜 저장 전에 정규화하는가</h2>
 * {@code 010-1234-5678}, {@code 010 1234 5678}, {@code +82 10-1234-5678} 은 같은 번호다.
 * 모양 그대로 저장하면 같은 번호가 두 계정의 로그인 ID 가 될 수 있고, 로그인할 때 가입
 * 당시와 다른 모양으로 입력한 사람은 "없는 계정"이 된다. 그래서 들어오는 즉시 숫자만
 * 남긴 {@code 01012345678} 로 바꾸고, DB 도 그 모양만 받는다 ({@code chk_users_phone_digits}).
 *
 * <h2>받는 범위</h2>
 * 휴대폰만 받는다 ({@code 010} 과 아직 쓰이는 {@code 011·016·017·018·019}). 유선번호는
 * SMS 를 받을 수 없어 로그인 ID 가 될 수 없다.
 */
public final class PhoneNumber {

    private static final Pattern MOBILE = Pattern.compile("^01[016789][0-9]{7,8}$");
    private static final Pattern NON_DIGIT = Pattern.compile("[^0-9]");

    private PhoneNumber() {
    }

    /**
     * 숫자만 남긴 휴대폰번호. 휴대폰번호가 아니면 {@code null}.
     *
     * <p>국가번호 {@code +82} 로 시작하면 앞자리 {@code 0} 으로 되돌린다 — 해외 로밍 중인
     * 사람의 폰이 번호를 그 모양으로 채워 넣는다.
     */
    public static String normalizeOrNull(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = NON_DIGIT.matcher(raw).replaceAll("");
        if (digits.startsWith("82") && (digits.length() == 11 || digits.length() == 12)) {
            digits = "0" + digits.substring(2);
        }
        return MOBILE.matcher(digits).matches() ? digits : null;
    }

    /** 숫자만 남긴 휴대폰번호. 휴대폰번호가 아니면 입력 오류. */
    public static String normalize(String raw) {
        String normalized = normalizeOrNull(raw);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "휴대폰번호 형식이 올바르지 않습니다.");
        }
        return normalized;
    }

    public static boolean isValid(String raw) {
        return normalizeOrNull(raw) != null;
    }
}
