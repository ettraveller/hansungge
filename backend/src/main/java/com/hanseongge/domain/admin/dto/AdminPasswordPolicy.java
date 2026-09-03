package com.hanseongge.domain.admin.dto;

/**
 * 관리자 비밀번호 규칙.
 *
 * <p>규칙을 상수로 모아두는 이유: 생성·변경·초기화 세 군데에 같은 정규식을 복사해 두면
 * 한 곳만 고쳐지는 날이 온다. 그때부터는 "정책이 무엇인가"에 답할 수 없게 된다.
 *
 * <p>상한 72자는 취향이 아니라 <b>BCrypt 의 한계</b>다. BCrypt 는 72바이트를 넘는
 * 입력을 조용히 잘라내므로, 그보다 긴 비밀번호를 허용하면 뒷부분이 검증되지 않는다.
 */
public final class AdminPasswordPolicy {

    /** 영문·숫자·특수문자를 각각 1자 이상 포함한 10~72자. */
    public static final String PATTERN =
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,72}$";

    public static final String MESSAGE =
            "비밀번호는 영문·숫자·특수문자를 모두 포함한 10자 이상 72자 이하여야 합니다.";

    private AdminPasswordPolicy() {
    }
}
