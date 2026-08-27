package com.hanseongge.domain.user.service;

import com.hanseongge.domain.setting.service.SystemSettingService;
import com.hanseongge.domain.user.dto.AuthProvidersResponse;
import com.hanseongge.domain.user.entity.AgreementType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 현재 시행 중인 약관 버전.
 *
 * <p>버전을 코드 상수가 아니라 {@code system_settings} 에 두는 이유: 약관 개정은 법무·운영이
 * 하는 일이고 개발 배포 주기와 무관하게 일어난다. 상수로 두면 문구를 고칠 때마다 배포가
 * 필요하고, 그 사이 동의 이력에는 <b>틀린 버전</b>이 기록된다.
 *
 * <p>설정 행이 없으면 {@link #FALLBACK_VERSION} 을 쓴다. 버전을 몰라 가입 자체가 막히는
 * 것보다는, 최초 버전으로 기록하고 운영이 값을 채우는 편이 낫다.
 */
@Service
@RequiredArgsConstructor
public class AgreementCatalog {

    /** 설정 키 접두사. 예) AGREEMENT_TERMS_VERSION */
    private static final String KEY_PREFIX = "AGREEMENT_";
    private static final String KEY_SUFFIX = "_VERSION";
    static final String FALLBACK_VERSION = "1.0";

    private final SystemSettingService systemSettingService;

    public String versionOf(AgreementType type) {
        String value = systemSettingService.get(KEY_PREFIX + type.name() + KEY_SUFFIX, FALLBACK_VERSION);
        return value == null || value.isBlank() ? FALLBACK_VERSION : value;
    }

    /** 가입 화면에 내려보낼 약관 목록. 필수가 먼저 오도록 정렬한다. */
    public List<AuthProvidersResponse.AgreementItem> all() {
        return Arrays.stream(AgreementType.values())
                .sorted((a, b) -> Boolean.compare(b.isRequired(), a.isRequired()))
                .map(type -> new AuthProvidersResponse.AgreementItem(
                        type, type.displayName(), type.isRequired(), versionOf(type)))
                .toList();
    }
}
