package com.hanseongge.global.util;

/**
 * 화면에 나가는 개인정보 가리기.
 *
 * <h2>가리는 일을 서버가 하는 이유</h2>
 * 화면에서 잘라도 <b>원본은 이미 응답에 실려 나간 뒤</b>다. HTML 소스와 개발자도구에
 * 그대로 남고, 목록 API 를 직접 부르면 전부 보인다. 가려진 것은 눈뿐이다.
 *
 * <p>그래서 가려야 하는 응답 DTO 는 원본 필드를 <b>담지 않는다.</b> 여기서 가린 값만 담는다.
 *
 * <h2>어디까지 가리는가</h2>
 * 게시판처럼 아무나 보는 화면은 "다른 사람이 쓴 글"이라는 구분만 되면 되므로 많이 가린다.
 * 관리자 회원 목록은 <b>같은 사람을 찾아내는 것</b>이 목적이라 조금 덜 가린다 — 하지만
 * 목록에서 전부 드러낼 이유는 없다. 전체 값이 필요하면 상세로 들어가고, 그 조회는
 * 감사로그에 남는다.
 */
public final class PrivacyMask {

    /** 값이 없는 경우의 표시. 빈 칸을 두면 표가 무너지고 "누락"인지 "없음"인지 모른다. */
    private static final String NONE = "-";

    /** 이름이 없는 회원(카카오 선택동의 거부 등). */
    private static final String ANONYMOUS = "회원";

    private static final char MASK = '*';

    private PrivacyMask() {
    }

    /**
     * 이름 — 첫 글자만 남긴다. {@code 홍길동 → 홍**}, {@code 김수 → 김*}
     *
     * <p>가운데만 가리는 방식({@code 홍*동})을 쓰지 않는다. 성과 끝 글자가 보이면 같은
     * 화면의 다른 정보와 맞춰 볼 여지가 생긴다.
     */
    public static String name(String name) {
        if (isBlank(name)) {
            return ANONYMOUS;
        }
        String trimmed = name.trim();
        if (trimmed.length() == 1) {
            return trimmed + MASK;
        }
        return trimmed.charAt(0) + repeat(trimmed.length() - 1);
    }

    /**
     * 이메일 — 아이디의 앞 두 글자만 남기고 도메인은 그대로 둔다.
     * {@code hongildong@example.com → ho********@example.com}
     *
     * <p>도메인을 남기는 이유는 관리자가 <b>같은 사람인지 판단</b>해야 하기 때문이다.
     * 가입 경로를 되짚을 때 회사 메일인지 네이버인지가 단서가 된다.
     */
    public static String email(String email) {
        if (isBlank(email)) {
            return NONE;
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        // @ 가 없으면 이메일 형태가 아니다. 통째로 가린다 — 무엇인지 모르는 값을
        // 부분만 노출하는 쪽이 더 위험하다
        if (at <= 0) {
            return repeat(trimmed.length());
        }

        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at);
        int keep = Math.min(2, local.length());
        return local.substring(0, keep) + repeat(local.length() - keep) + domain;
    }

    /**
     * 휴대폰 — 가운데 자리만 가린다. {@code 010-1234-5678 → 010-****-5678}
     *
     * <p>끝 네 자리를 남기는 것은 관행이고 실용적이다. 고객이 전화로 "뒷자리 5678" 이라고
     * 말하면 그것만으로 계정을 찾을 수 있다.
     */
    public static String phone(String phone) {
        if (isBlank(phone)) {
            return NONE;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 7) {
            return repeat(digits.length());
        }
        String head = digits.substring(0, 3);
        String tail = digits.substring(digits.length() - 4);
        return "%s-%s-%s".formatted(head, repeat(4), tail);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String repeat(int count) {
        return count <= 0 ? "" : String.valueOf(MASK).repeat(count);
    }
}
