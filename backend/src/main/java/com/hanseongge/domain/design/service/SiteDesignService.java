package com.hanseongge.domain.design.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.design.dto.SiteDesign;
import com.hanseongge.domain.setting.service.SystemSettingService;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.audit.AuditLog;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 화면 꾸미기 — 메인 구역의 차례·제목과 구역별 색.
 *
 * <h2>한 덩어리(JSON)로 둔다</h2>
 * 구역 차례는 묶음으로만 뜻이 있다. 칸마다 따로 저장하면 차례를 바꾸는 도중의
 * <b>반쯤 바뀐 메인</b>이 손님 화면에 걸린다. {@code system_settings} 의 한 줄에
 * 통째로 넣고 통째로 갈아 끼운다.
 *
 * <h2>받은 값을 그대로 넣지 않는다</h2>
 * 색은 손님 화면의 {@code <style>} 에, 제목은 메인 한가운데에 그대로 나간다. 그래서
 * 저장하기 전에 <b>아는 이름만, 아는 모양으로</b> 다시 짠다 — 모르는 구역·모르는 색
 * 이름은 버리고, 색은 {@code #rrggbb} 가 아니면 비우고, 빠진 구역은 기본 자리에 채운다.
 * 읽을 때도 같은 다듬기를 한 번 더 거친다. DB 의 값이 손으로 고쳐졌거나 구역 목록이
 * 코드에서 바뀌었을 때 메인이 500 으로 떨어지지 않게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteDesignService {

    static final String SETTING_KEY = "SITE_DESIGN";

    /** 메인의 구역. 이 차례가 기본 차례다. 화면(HomeView)이 같은 이름을 쓴다. */
    public static final List<String> SECTION_KEYS = List.of("PRICE", "PRODUCTS", "STORES", "NOTICES");

    /** 고를 수 있는 색 자리. 화면(siteDesign.ts)이 같은 이름을 CSS 변수에 잇는다. */
    public static final List<String> COLOR_KEYS = List.of(
            "accent",
            "topbarBg", "topbarFg",
            "headerBg", "headerFg",
            "priceTitle",
            "buttonBg", "buttonFg",
            "footerBg", "footerFg");

    static final int MAX_TITLE = 40;
    static final int MAX_DESC = 80;

    private static final Pattern HEX = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final SystemSettingService settingService;
    private final ObjectMapper objectMapper;

    public SiteDesign current() {
        String raw = settingService.get(SETTING_KEY, "");
        if (raw == null || raw.isBlank()) {
            return normalize(null);
        }
        try {
            return normalize(objectMapper.readValue(raw, SiteDesign.class));
        } catch (JsonProcessingException e) {
            log.error("[DESIGN] 저장된 화면 설정을 읽지 못했습니다. 기본값으로 그립니다.", e);
            return normalize(null);
        }
    }

    @AuditLog(action = AuditActions.SITE_DESIGN_SAVE, targetType = AuditActions.TARGET_SITE_DESIGN)
    public SiteDesign save(SiteDesign requested, String actor) {
        SiteDesign design = normalize(requested);
        try {
            settingService.update(SETTING_KEY, objectMapper.writeValueAsString(design), actor);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "화면 설정을 저장하지 못했습니다.");
        }
        log.info("[DESIGN] 화면 설정 저장. by={}", actor);
        return design;
    }

    /** 아는 이름만, 아는 모양으로 다시 짠다. null 이면 기본값이다. */
    static SiteDesign normalize(SiteDesign raw) {
        List<SiteDesign.Section> sections = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        if (raw != null && raw.sections() != null) {
            for (SiteDesign.Section section : raw.sections()) {
                // 모르는 구역은 버리고, 같은 구역이 두 번 오면 처음 것만 쓴다.
                if (section == null || !SECTION_KEYS.contains(section.key()) || !seen.add(section.key())) {
                    continue;
                }
                sections.add(new SiteDesign.Section(section.key(), section.on(),
                        clip(section.title(), MAX_TITLE), clip(section.desc(), MAX_DESC)));
            }
        }
        // 빠진 구역은 켠 채로 뒤에 붙인다 — 요청에서 빠졌다고 메인에서 사라지면 안 된다.
        for (String key : SECTION_KEYS) {
            if (seen.add(key)) {
                sections.add(new SiteDesign.Section(key, true, "", ""));
            }
        }

        Map<String, String> colors = new LinkedHashMap<>();
        for (String key : COLOR_KEYS) {
            String value = raw == null || raw.colors() == null ? null : raw.colors().get(key);
            colors.put(key, hexOrBlank(value));
        }
        return new SiteDesign(List.copyOf(sections), colors);
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String hexOrBlank(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        return HEX.matcher(trimmed).matches() ? trimmed.toLowerCase(Locale.ROOT) : "";
    }
}
