package com.hanseongge.domain.design.dto;

import java.util.List;
import java.util.Map;

/**
 * 손님 화면의 겉모습 — 메인 구역의 차례와 구역별 색.
 *
 * <p>{@code sections} 는 메인에 쌓이는 차례 그대로다. 배너는 여기 없다 — 언제나 맨 위이고
 * 따로 관리한다(메인 배너). {@code colors} 는 「자리 이름 → #rrggbb」 이고, 빈 값은
 * "고르지 않았다 = 기본색"이다.
 */
public record SiteDesign(List<Section> sections, Map<String, String> colors) {

    /**
     * 메인의 한 구역.
     *
     * @param key   어느 구역인가 ({@code SiteDesignService.SECTION_KEYS})
     * @param on    내걸었는가
     * @param title 제목. 비우면 그 구역의 기본 제목
     * @param desc  제목 아래 한 줄. 비우면 기본 문구
     */
    public record Section(String key, boolean on, String title, String desc) {}
}
