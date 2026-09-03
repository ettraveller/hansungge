package com.hanseongge.global.util;

/**
 * 공개 화면에 나가는 이름 가리기.
 *
 * <h2>가리는 일을 서버가 하는 이유</h2>
 * 화면에서 잘라도 <b>실명은 이미 응답에 실려 나간 뒤</b>다. HTML 소스나 개발자도구에
 * 그대로 남고, 목록 API 를 직접 부르면 전부 보인다. 가려진 것은 눈뿐이다.
 *
 * <p>그래서 공개 응답 DTO 는 원본 이름을 <b>담지 않는다.</b> 여기서 가린 값만 담는다.
 */
public final class NameMasker {

    /** 이름이 없는 회원(카카오 선택동의 거부 등)에게 쓸 표시. */
    private static final String ANONYMOUS = "회원";

    private static final char MASK = '*';

    private NameMasker() {
    }

    /**
     * 첫 글자만 남기고 가린다. {@code 홍길동 → 홍**}, {@code 김수 → 김*}
     *
     * <p>가운데만 가리는 방식({@code 홍*동})을 쓰지 않는다. 성과 끝 글자가 보이면 같은
     * 게시판의 다른 정보와 맞춰 볼 여지가 생긴다. 게시판에서 필요한 것은 "다른 사람이
     * 쓴 글"이라는 구분뿐이지 누구인지가 아니다.
     */
    public static String mask(String name) {
        if (name == null || name.isBlank()) {
            return ANONYMOUS;
        }
        String trimmed = name.trim();
        if (trimmed.length() == 1) {
            return trimmed + MASK;
        }
        return trimmed.charAt(0) + String.valueOf(MASK).repeat(trimmed.length() - 1);
    }
}
