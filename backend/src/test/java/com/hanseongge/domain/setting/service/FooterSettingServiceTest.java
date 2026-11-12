package com.hanseongge.domain.setting.service;

import com.hanseongge.domain.setting.FooterField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 푸터 저장이 <b>푸터 키만</b> 건드리는지 확인한다.
 *
 * <p>이 서비스는 「이름 → 값」 지도를 그대로 받는다. 목록 밖의 이름이 섞여 와도
 * 쓰이지 않아야 한다 — 같은 표({@code system_settings})에 시세 자동적용 허용
 * 변동률처럼 잘못 건드리면 값이 조용히 틀어지는 것이 들어 있다. 푸터 글자를 고치러
 * 들어온 요청으로 그것을 바꿀 수 있으면 안 된다.
 *
 * <p>DB 없이 {@link SystemSettingService} 를 가짜로 세워 "무엇이 쓰였나"만 본다.
 */
class FooterSettingServiceTest {

    /** 쓰인 키를 기억하는 가짜. 캐시·트랜잭션은 이 테스트의 관심이 아니다. */
    private static class FakeSettingService extends SystemSettingService {
        final Map<String, String> written = new LinkedHashMap<>();

        FakeSettingService() {
            super(null);
        }

        @Override
        public String get(String key, String defaultValue) {
            return written.getOrDefault(key, defaultValue);
        }

        @Override
        public com.hanseongge.domain.setting.entity.SystemSetting update(
                String key, String value, String updatedBy) {
            written.put(key, value);
            return null;
        }
    }

    private FakeSettingService settings;
    private FooterSettingService footer;

    @BeforeEach
    void setUp() {
        settings = new FakeSettingService();
        footer = new FooterSettingService(settings);
    }

    @Test
    @DisplayName("목록 밖의 이름은 아무것도 쓰지 않는다")
    void ignoresUnknownFields() {
        Map<String, String> request = new HashMap<>();
        request.put("address", "서울특별시 종로구 1-1");
        request.put("MAX_PRICE_CHANGE_PERCENT", "999");
        request.put("BANK_ACCOUNT_NUMBER", "000-000");

        footer.save(request, "tester");

        assertThat(settings.written).containsOnlyKeys(FooterField.ADDRESS.settingKey());
        assertThat(settings.written.get(FooterField.ADDRESS.settingKey()))
                .isEqualTo("서울특별시 종로구 1-1");
    }

    @Test
    @DisplayName("보내지 않은 칸은 건드리지 않는다 — 빈 값으로 덮으면 나머지가 지워진다")
    void leavesAbsentFieldsAlone() {
        footer.save(Map.of("tel", "02-000-0000"), "tester");

        assertThat(settings.written).containsOnlyKeys(FooterField.TEL.settingKey());
    }

    @Test
    @DisplayName("빈 문자열로 보낸 칸은 지운 것으로 본다 — 통신판매업신고번호를 내리는 길이다")
    void blankClearsField() {
        Map<String, String> request = new HashMap<>();
        request.put("mailOrderNo", "   ");

        footer.save(request, "tester");

        assertThat(settings.written.get(FooterField.MAIL_ORDER_NO.settingKey())).isEmpty();
    }

    @Test
    @DisplayName("너무 긴 값은 칸 길이로 자른다 — 거절하면 나머지 수정이 함께 날아간다")
    void truncatesInsteadOfRejecting() {
        String tooLong = "가".repeat(FooterField.ADDRESS.maxLength() + 50);

        footer.save(Map.of("address", tooLong), "tester");

        assertThat(settings.written.get(FooterField.ADDRESS.settingKey()))
                .hasSize(FooterField.ADDRESS.maxLength());
    }

    @Test
    @DisplayName("저장된 적 없는 칸은 코드의 기본값이 나간다")
    void fallsBackToDefaults() {
        Map<String, String> current = footer.current();

        assertThat(current).containsOnlyKeys(FooterField.emptyMap().keySet().toArray(String[]::new));
        assertThat(current.get("companyName")).isEqualTo(FooterField.COMPANY_NAME.defaultValue());
    }
}
