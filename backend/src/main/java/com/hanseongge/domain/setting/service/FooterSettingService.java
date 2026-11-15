package com.hanseongge.domain.setting.service;

import com.hanseongge.domain.setting.FooterField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 푸터 글자 조회/저장.
 *
 * <p>{@link SystemSettingService} 위에 얇게 얹는다 — 캐시·변경 로그·키 생성은 그쪽이
 * 이미 한다. 여기가 하는 일은 <b>{@link FooterField} 목록 밖의 키는 절대 건드리지
 * 않는 것</b>뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FooterSettingService {

    private final SystemSettingService settingService;

    /** 지금 푸터에 나가는 값. DB 에 없는 칸은 코드의 기본값이 나간다. */
    public Map<String, String> current() {
        Map<String, String> values = new LinkedHashMap<>();
        for (FooterField field : FooterField.values()) {
            values.put(field.fieldName(),
                    settingService.get(field.settingKey(), field.defaultValue()));
        }
        return values;
    }

    /**
     * 받은 칸만 저장한다.
     *
     * <p>요청에 없는 칸은 <b>건드리지 않는다</b> — 빈 값으로 덮으면 화면이 보내지 않은
     * 칸까지 지워진다. 반대로 <b>빈 문자열로 온 칸은 지운 것으로 본다</b>: 통신판매업
     * 신고번호처럼 비워 두는 것이 정답인 칸이 있고, 지우는 길이 그것이다.
     *
     * <p>{@link FooterField} 목록을 돌면서 그 이름이 요청에 있는지 보는 방식이다.
     * 요청을 돌면서 키를 찾는 방식이 아니다 — 그러면 목록 밖의 이름이 섞여 올 때
     * 거르는 코드를 따로 적어야 하고, 그 한 줄을 빠뜨리면 아무 설정값이나 쓰이게 된다.
     */
    public Map<String, String> save(Map<String, String> requested, String actor) {
        for (FooterField field : FooterField.values()) {
            if (requested == null || !requested.containsKey(field.fieldName())) {
                continue;
            }
            String value = field.normalize(requested.get(field.fieldName()));
            settingService.update(field.settingKey(), value, actor);
        }
        return current();
    }
}
