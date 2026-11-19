package com.hanseongge.domain.design.service;

import com.hanseongge.domain.design.dto.SiteDesign;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 화면 설정이 <b>아는 이름·아는 모양만</b> 남기는지 확인한다.
 *
 * <p>이 값은 손님 화면의 {@code <style>} 과 메인 한가운데에 그대로 나간다.
 */
class SiteDesignServiceTest {

    private static SiteDesign.Section section(String key, boolean on, String title) {
        return new SiteDesign.Section(key, on, title, "");
    }

    @Test
    @DisplayName("저장된 것이 없으면 기본 차례, 전부 켜짐, 색은 전부 빈 값이다")
    void defaults() {
        SiteDesign design = SiteDesignService.normalize(null);

        assertThat(design.sections()).extracting(SiteDesign.Section::key)
                .containsExactlyElementsOf(SiteDesignService.SECTION_KEYS);
        assertThat(design.sections()).allMatch(SiteDesign.Section::on);
        assertThat(design.colors()).containsOnlyKeys(SiteDesignService.COLOR_KEYS.toArray(String[]::new));
        assertThat(design.colors().values()).allMatch(String::isEmpty);
    }

    @Test
    @DisplayName("보낸 차례를 지키고, 빠진 구역은 켠 채로 뒤에 붙인다")
    void keepsOrderAndFillsMissing() {
        SiteDesign design = SiteDesignService.normalize(new SiteDesign(
                List.of(section("NOTICES", true, " 소식 "), section("PRICE", false, "")), Map.of()));

        assertThat(design.sections()).extracting(SiteDesign.Section::key)
                .containsExactly("NOTICES", "PRICE", "PRODUCTS", "STORES");
        assertThat(design.sections().get(0).title()).isEqualTo("소식");
        assertThat(design.sections().get(1).on()).isFalse();
        assertThat(design.sections().get(2).on()).isTrue();
    }

    @Test
    @DisplayName("모르는 구역은 버리고, 같은 구역이 두 번 오면 처음 것만 쓴다")
    void dropsUnknownAndDuplicateSections() {
        SiteDesign design = SiteDesignService.normalize(new SiteDesign(
                Arrays.asList(section("HACK", true, "x"), null, section("STORES", false, "a"),
                        section("STORES", true, "b")), null));

        assertThat(design.sections()).hasSize(SiteDesignService.SECTION_KEYS.size());
        assertThat(design.sections().get(0).key()).isEqualTo("STORES");
        assertThat(design.sections().get(0).title()).isEqualTo("a");
    }

    @Test
    @DisplayName("제목과 설명은 칸 길이로 자른다")
    void clipsText() {
        SiteDesign design = SiteDesignService.normalize(new SiteDesign(
                List.of(new SiteDesign.Section("PRICE", true, "가".repeat(100), "나".repeat(200))), null));

        assertThat(design.sections().get(0).title()).hasSize(SiteDesignService.MAX_TITLE);
        assertThat(design.sections().get(0).desc()).hasSize(SiteDesignService.MAX_DESC);
    }

    @Test
    @DisplayName("색은 #rrggbb 만 남는다 — style 에 그대로 들어가는 값이다")
    void colorsAcceptOnlyHex() {
        Map<String, String> colors = new HashMap<>();
        colors.put("accent", " #861F41 ");
        colors.put("topbarBg", "red");
        colors.put("headerBg", "#fff");
        colors.put("footerBg", "#ffffff;} body{display:none");
        colors.put("buttonBg", "url(javascript:alert(1))");
        colors.put("notAColor", "#000000");

        SiteDesign design = SiteDesignService.normalize(new SiteDesign(null, colors));

        assertThat(design.colors().get("accent")).isEqualTo("#861f41");
        assertThat(design.colors().get("topbarBg")).isEmpty();
        assertThat(design.colors().get("headerBg")).isEmpty();
        assertThat(design.colors().get("footerBg")).isEmpty();
        assertThat(design.colors().get("buttonBg")).isEmpty();
        assertThat(design.colors()).doesNotContainKey("notAColor");
    }
}
