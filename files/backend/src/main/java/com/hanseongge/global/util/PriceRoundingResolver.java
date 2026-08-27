package com.hanseongge.global.util;

import com.hanseongge.domain.setting.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 최종가 반올림 정책 결정자.
 *
 * <p>{@code system_settings.PRICE_ROUNDING_MODE} 를 읽어 {@link RoundingPolicy} 를 돌려준다.
 *
 * <h2>미확정(TODO A-2) 처리 원칙</h2>
 * 설정이 {@code TBD} 이거나 알 수 없는 값이면 <b>예외를 던지지 않는다.</b>
 * 반올림 정책이 아직 안 정해졌다는 이유로 상품 조회·주문이 전부 막히면
 * 개발과 검수가 진행되지 않기 때문이다.
 *
 * <p>대신 {@link RoundingPolicy#TEMPORARY_DEFAULT}(원단위 절사)로 <b>임시 동작</b>하고
 * WARN 로그를 남긴다. 로그는 상태가 바뀔 때만 한 번씩 찍어 로그 폭주를 막는다 —
 * 매 계산마다 찍으면 정작 중요한 경고가 묻힌다.
 *
 * <p><b>A-2 확정 시 코드 수정은 필요 없다.</b> {@code system_settings} 값만 바꾸면 된다.
 * 다만 주문 Snapshot 은 생성 시점 정책을 그대로 보관하므로(설계서 §10.3
 * {@code rounding_policy} 컬럼), 이미 만들어진 주문 금액은 바뀌지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PriceRoundingResolver {

    public static final String SETTING_KEY = "PRICE_ROUNDING_MODE";

    private final SystemSettingService systemSettingService;

    /** 미확정 상태 경고를 이미 남겼는지. 로그 폭주 방지용. */
    private final AtomicBoolean undecidedWarned = new AtomicBoolean(false);
    /** 알 수 없는 값 경고를 이미 남겼는지. */
    private final AtomicBoolean unknownWarned = new AtomicBoolean(false);

    public RoundingPolicy resolve() {
        Optional<String> raw = systemSettingService.find(SETTING_KEY);

        if (raw.isEmpty()) {
            if (undecidedWarned.compareAndSet(false, true)) {
                log.warn("""
                        [TODO A-2] {} 가 아직 확정되지 않았습니다(TBD).
                        임시로 '{}'(원단위 절사)를 적용합니다.
                        발주자 확정 후 system_settings 값을 변경하면 재배포 없이 반영됩니다.""",
                        SETTING_KEY, RoundingPolicy.TEMPORARY_DEFAULT.name());
            }
            return RoundingPolicy.TEMPORARY_DEFAULT;
        }

        String value = raw.get();
        Optional<RoundingPolicy> parsed = RoundingPolicy.parse(value);

        if (parsed.isEmpty()) {
            if (unknownWarned.compareAndSet(false, true)) {
                log.warn("[TODO A-2] {} 에 알 수 없는 값이 설정되어 있습니다: '{}'. 임시로 '{}' 를 적용합니다.",
                        SETTING_KEY, value, RoundingPolicy.TEMPORARY_DEFAULT.name());
            }
            return RoundingPolicy.TEMPORARY_DEFAULT;
        }

        // 정상 설정으로 복귀하면 다음 미확정 상황에서 다시 경고할 수 있게 리셋한다.
        undecidedWarned.set(false);
        unknownWarned.set(false);
        return parsed.get();
    }

    /** 현재 정책이 A-2 확정 전 임시값인지. 관리자 대시보드 경고 표시에 쓴다. */
    public boolean isUsingTemporaryDefault() {
        return systemSettingService.find(SETTING_KEY)
                .flatMap(RoundingPolicy::parse)
                .isEmpty();
    }

    /** 최종 판매가에 반올림 정책을 적용한다. 계산 파이프라인의 마지막에 단 한 번만 호출한다. */
    public Money round(Money amount) {
        return amount.applyRounding(resolve());
    }
}
