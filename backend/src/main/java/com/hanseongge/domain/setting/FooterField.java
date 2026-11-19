package com.hanseongge.domain.setting;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 푸터에 들어가는 글자들.
 *
 * <h2>왜 한 곳에 목록으로 두는가</h2>
 * 칸마다 응답 필드·요청 필드·검증·관리자 폼을 따로 적으면 네 군데를 같이 고쳐야 하고,
 * 한 군데를 빠뜨리면 <b>저장은 되는데 화면에 안 나오는</b> 칸이 생긴다. 목록을 하나
 * 두고 넷이 모두 이것을 돌면 칸을 더하는 일이 한 줄이 된다.
 *
 * <h2>왜 {@code system_settings} 전체를 열지 않는가</h2>
 * 그 표에는 {@code MAX_PRICE_CHANGE_PERCENT}(시세 자동적용 허용 변동률)처럼 잘못
 * 건드리면 값이 조용히 틀어지는 것이 섞여 있다. 푸터 글자를 고치러 들어온 사람이
 * 시세 안전망을 끌 수 있게 되면 안 된다. 그래서 저장은 <b>이 목록에 있는 키만</b>
 * 쓴다 — 요청에 다른 이름이 섞여 와도 그 키로는 아무것도 쓰이지 않는다.
 *
 * <h2>기본값은 코드에 남긴다</h2>
 * DB 에 값이 없으면 여기 적힌 값이 나간다. 그래서 설정을 한 번도 저장하지 않은
 * 환경(새로 띄운 QA)에서도 푸터가 비어 보이지 않는다.
 */
public enum FooterField {

    COMPANY_NAME("companyName", "FOOTER_COMPANY_NAME", "주식회사 금한성", 80),
    CEO("ceo", "FOOTER_CEO", "강성구", 40),
    BIZ_NO("bizNo", "FOOTER_BIZ_NO", "702-86-03859", 40),

    /**
     * 통신판매업신고번호.
     *
     * <p>아직 받지 못했다. 비워 두면 화면이 그 줄을 그리지 않는다 — 고객이 읽는
     * 자리에 틀린 신고번호를 두는 것이 비워 두는 것보다 나쁘다. 받는 날 이 칸에
     * 적으면 되고, 배포가 필요하지 않다.
     */
    MAIL_ORDER_NO("mailOrderNo", "FOOTER_MAIL_ORDER_NO", "", 60),

    /**
     * 사업장 소재지.
     *
     * <p>⚠️ 전자상거래법 §13 이 표시하라는 값이고 사업자등록증과 같아야 한다.
     * 개인정보처리방침 §13 · 이용약관의 「사업장 소재지」와 한 쌍이다 — 여기만
     * 고치면 같은 회사 주소가 화면마다 달라진다.
     */
    ADDRESS("address", "FOOTER_ADDRESS",
            "서울특별시 종로구 돈화문로6나길 45, 3층 302호 (봉익동, 보석백화점)", 200),

    EMAIL("email", "FOOTER_EMAIL", "hanseongge@gmail.com", 120),
    TAGLINE("tagline", "FOOTER_TAGLINE", "정직과 신뢰를 바탕으로 고객의 투자를 함께 설계합니다.", 200),
    TEL("tel", "FOOTER_TEL", "02-743-4176", 40),
    HOURS("hours", "FOOTER_HOURS", "월-토 10:00 ~ 19:00", 80),
    HOLIDAY("holiday", "FOOTER_HOLIDAY", "일요일·공휴일 휴무", 80);

    private final String name;
    private final String settingKey;
    private final String defaultValue;
    private final int maxLength;

    FooterField(String name, String settingKey, String defaultValue, int maxLength) {
        this.name = name;
        this.settingKey = settingKey;
        this.defaultValue = defaultValue;
        this.maxLength = maxLength;
    }

    /** 화면이 쓰는 이름 (JSON 키). */
    public String fieldName() {
        return name;
    }

    /** {@code system_settings} 의 키. */
    public String settingKey() {
        return settingKey;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public int maxLength() {
        return maxLength;
    }

    /**
     * 받은 값을 이 칸에 넣을 수 있는 모양으로 다듬는다.
     *
     * <p>앞뒤 공백을 떼고 길이를 자른다. 길다고 거절하지 않는 이유는, 거절하면 열 칸을
     * 채워 저장을 눌렀을 때 <b>어느 칸이 문제인지</b>를 화면이 알려 줘야 하고, 그 줄
     * 하나 때문에 나머지 아홉 칸의 수정이 함께 날아가기 때문이다. 길이는 화면에서도
     * {@code maxLength} 로 막는다 — 여기 자르기는 그 뒤의 마지막 그물이다.
     */
    public String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }

    public static Optional<FooterField> byFieldName(String name) {
        return Arrays.stream(values()).filter(f -> f.name.equals(name)).findFirst();
    }

    /** 칸 이름 → 값. 화면에 내보내는 모양이다. 순서는 푸터에 보이는 순서다. */
    public static Map<String, String> emptyMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (FooterField field : values()) {
            map.put(field.name, field.defaultValue);
        }
        return map;
    }
}
