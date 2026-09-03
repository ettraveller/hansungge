package com.hanseongge.domain.setting.service;

import com.hanseongge.domain.setting.entity.SystemSetting;
import com.hanseongge.domain.setting.repository.SystemSettingRepository;
import com.hanseongge.global.config.CacheConfig;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 운영 설정값 조회/갱신 서비스.
 *
 * <p>설정값은 거의 바뀌지 않는데 가격 계산·시세 검증 경로에서 매우 자주 읽힌다.
 * 그래서 Caffeine 캐시를 앞에 둔다 (설계서 §7.3 — Redis 는 트래픽 발생 후 도입).
 * 갱신 시에는 해당 키만 무효화한다.
 *
 * <p>미확정 설정값('TBD')을 만나도 예외를 던지지 않는다. 확정 대기 항목(A-1 부가세,
 * A-2 반올림) 때문에 서비스 전체가 멈추면 안 되기 때문이다. 대신 호출자가
 * {@link Optional#empty()} 를 받아 임시 동작 + WARN 을 선택하게 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingService {

    /** 아직 확정되지 않은 설정값을 나타내는 약속된 문자열. */
    public static final String UNDECIDED = "TBD";

    private final SystemSettingRepository systemSettingRepository;

    /**
     * 설정값 조회.
     *
     * <p>키가 없거나 값이 {@code TBD} 면 {@link Optional#empty()}.
     * "설정이 없다"와 "설정이 미확정이다"를 호출자 입장에서 같게 취급한다 —
     * 둘 다 "확정된 운영 정책이 아직 없다"는 뜻이고 대응도 같기 때문이다.
     */
    @Cacheable(cacheNames = CacheConfig.SYSTEM_SETTINGS, key = "#key", sync = true)
    @Transactional(readOnly = true)
    public Optional<String> find(String key) {
        return systemSettingRepository.findById(key)
                .map(SystemSetting::getValue)
                .filter(v -> !v.isBlank())
                .filter(v -> !UNDECIDED.equalsIgnoreCase(v.trim()));
    }

    /** 설정값 조회. 없으면 기본값. */
    public String get(String key, String defaultValue) {
        return find(key).orElse(defaultValue);
    }

    /** 설정값 조회. 없으면 예외 — 없으면 안 되는 값에만 쓴다. */
    public String getRequired(String key) {
        return find(key).orElseThrow(() ->
                new BusinessException(ErrorCode.SETTING_NOT_FOUND, "설정값 없음/미확정: " + key));
    }

    public int getInt(String key, int defaultValue) {
        return find(key).map(v -> {
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                log.warn("[SETTING] {} 값이 정수가 아닙니다. 기본값 {} 사용. value={}", key, defaultValue, v);
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    public BigDecimal getDecimal(String key, BigDecimal defaultValue) {
        return find(key).map(v -> {
            try {
                return new BigDecimal(v.trim());
            } catch (NumberFormatException e) {
                log.warn("[SETTING] {} 값이 숫자가 아닙니다. 기본값 {} 사용. value={}", key, defaultValue, v);
                return defaultValue;
            }
        }).orElse(defaultValue);
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> findAll() {
        return systemSettingRepository.findAll();
    }

    /**
     * 설정값 갱신. 존재하지 않는 키면 새로 만든다.
     *
     * <p>캐시는 해당 키만 무효화한다 — 전체를 비우면 무관한 설정까지 DB 재조회가 몰린다.
     */
    @CacheEvict(cacheNames = CacheConfig.SYSTEM_SETTINGS, key = "#key")
    @Transactional
    public SystemSetting update(String key, String value, String updatedBy) {
        SystemSetting setting = systemSettingRepository.findById(key)
                .map(existing -> {
                    log.info("[SETTING] {} 변경: {} -> {} (by {})", key, existing.getValue(), value, updatedBy);
                    existing.update(value, updatedBy);
                    return existing;
                })
                .orElseGet(() -> {
                    log.info("[SETTING] {} 신규 생성: {} (by {})", key, value, updatedBy);
                    return systemSettingRepository.save(
                            SystemSetting.create(key, value, null, updatedBy));
                });
        return setting;
    }

    /** 외부에서 DB 를 직접 수정했을 때를 위한 전체 무효화. 운영 도구용. */
    @CacheEvict(cacheNames = CacheConfig.SYSTEM_SETTINGS, allEntries = true)
    public void evictAll() {
        log.info("[SETTING] 설정 캐시 전체 무효화");
    }
}
