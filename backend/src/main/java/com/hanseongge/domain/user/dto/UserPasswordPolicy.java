package com.hanseongge.domain.user.dto;

/**
 * 회원 비밀번호 정책.
 *
 * <p>관리자({@code AdminPasswordPolicy})와 <b>같은 규칙을 쓴다.</b> 규칙을 둘로 나누면
 * "어느 쪽이 더 센가"를 따지게 되고, 약한 쪽이 결국 뚫린다. 관리자 계정이 더 위험하다고
 * 회원 쪽을 느슨하게 할 이유가 없다 — 회원 계정에는 주문 이력과 연락처가 들어 있다.
 */
public final class UserPasswordPolicy {

    /**
     * 영문·숫자·특수문자를 모두 포함한 10~72자.
     *
     * <p>상한 72자는 취향이 아니라 <b>BCrypt 의 한계</b>다. BCrypt 는 72바이트를 넘는
     * 입력을 조용히 잘라낸다 — 막지 않으면 80자를 넣은 사용자가 실제로는 72자만
     * 보호받으면서 그 사실을 모른다.
     */
    public static final String PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{10,72}$";

    public static final String MESSAGE =
            "비밀번호는 영문·숫자·특수문자를 모두 포함한 10자 이상 72자 이하여야 합니다.";

    private UserPasswordPolicy() {
    }
}
