package com.hanseongge.global.util;

import com.hanseongge.domain.setting.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 부가세 정책 결정자.
 *
 * <p>{@code system_settings.VAT_MODE} 를 읽어 {@link VatMode} 를 돌려준다.
 * {@link PriceRoundingResolver} 와 같은 구조·같은 원칙이다.
 *
 * <h2>미확정(TODO A-1) 처리 원칙</h2>
 * 설정이 {@code TBD} 이거나 알 수 없는 값이면 <b>예외를 던지지 않는다.</b> 부가세 정책이
 * 아직 안 정해졌다는 이유로 상품 조회가 전부 막히면 개발과 검수가 진행되지 않는다.
 * 대신 {@link VatMode#TEMPORARY_DEFAULT}(면세)로 임시 동작하고 WARN 을 남긴다.
 *
 * <p><b>A-1 확정 시 코드 수정은 필요 없다.</b> {@code system_settings} 값만 바꾸면 된다.
 * 다만 주문 Snapshot 은 생성 시점 정책을 그대로 보관하므로, 이미 만들어진 주문 금액은
 * 바뀌지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VatPolicyResolver {

    public static final String SETTING_KEY = "VAT_MODE";
    /** 부가세율(%) 설정 키. 없으면 10%. */
    public static final String RATE_SETTING_KEY = "VAT_RATE";

    /**
     * 「상품마다 정한다」 (V38). {@code VAT_MODE} 가 가질 수 있는 값이지만
     * <b>{@link VatMode} 의 값은 아니다.</b>
     *
     * <p>enum 에 넣지 않는 이유는 종류가 다르기 때문이다. {@code NONE/INCLUDED/EXCLUDED}
     * 는 "세액을 어떻게 계산하는가"이고, 이 값은 "그것을 어디서 정하는가"다. enum 에
     * 섞으면 {@link VatMode#vatOf} 가 계산할 수 없는 분기를 하나 떠안는다.
     */
    public static final String PER_PRODUCT = "PER_PRODUCT";

    private static final BigDecimal DEFAULT_RATE_PERCENT = BigDecimal.TEN;

    private final SystemSettingService systemSettingService;

    private final AtomicBoolean undecidedWarned = new AtomicBoolean(false);
    private final AtomicBoolean unknownWarned = new AtomicBoolean(false);

    public VatMode resolve() {
        Optional<String> raw = systemSettingService.find(SETTING_KEY);

        // 상품마다 정하는 중이면 전역 모드로 환산할 수 없다 — 면세 상품과 과세 상품이
        // 같은 가게에 있으므로 어느 쪽을 골라도 절반은 틀린다.
        //
        // 금액을 바꾸지 않는 NONE 을 준다. 지금 금액을 내는 경로는 상품을 직접 보므로
        // (PriceCalculationService 6단계) 이 값을 쓰지 않는다. WARN 도 남기지 않는다 —
        // 정책이 정해져 있는데 「미확정」이라고 적으면 그 로그를 보고 사람이 움직인다.
        if (raw.isPresent() && isPerProduct(raw.get())) {
            return VatMode.NONE;
        }

        if (raw.isEmpty()) {
            if (undecidedWarned.compareAndSet(false, true)) {
                log.warn("""
                        [TODO A-1] {} 가 아직 확정되지 않았습니다(TBD).
                        임시로 '{}'(면세)를 적용합니다 — 최종 금액이 소계와 같고 세액은 0입니다.
                        발주자·세무사 확정 후 system_settings 값을 변경하면 재배포 없이 반영됩니다.""",
                        SETTING_KEY, VatMode.TEMPORARY_DEFAULT.name());
            }
            return VatMode.TEMPORARY_DEFAULT;
        }

        Optional<VatMode> parsed = VatMode.parse(raw.get());
        if (parsed.isEmpty()) {
            if (unknownWarned.compareAndSet(false, true)) {
                log.warn("[TODO A-1] {} 에 알 수 없는 값이 설정되어 있습니다: '{}'. 임시로 '{}' 를 적용합니다.",
                        SETTING_KEY, raw.get(), VatMode.TEMPORARY_DEFAULT.name());
            }
            return VatMode.TEMPORARY_DEFAULT;
        }

        undecidedWarned.set(false);
        unknownWarned.set(false);
        return parsed.get();
    }

    /** 부가세율(%). 설정이 없거나 숫자가 아니면 10%. */
    public BigDecimal ratePercent() {
        return systemSettingService.getDecimal(RATE_SETTING_KEY, DEFAULT_RATE_PERCENT);
    }

    /**
     * 설정에 적힌 값 그대로. 정책을 <b>보고</b>하는 자리가 쓴다 (헬스체크).
     *
     * <p>{@link #resolve()} 를 쓰면 {@link #PER_PRODUCT} 가 {@code NONE} 으로 보여
     * "아직 면세로 임시 운영 중"처럼 읽힌다. 보고는 설정된 대로 해야 한다.
     *
     * <p>설정이 없으면 {@code "TBD"} — 그것도 사실이다.
     */
    public String rawMode() {
        return systemSettingService.find(SETTING_KEY)
                .map(value -> value.trim().toUpperCase())
                .filter(value -> !value.isEmpty())
                .orElse("TBD");
    }

    /** 현재 정책이 A-1 확정 전 임시값인지. 관리자 대시보드 경고 표시에 쓴다. */
    public boolean isUsingTemporaryDefault() {
        Optional<String> raw = systemSettingService.find(SETTING_KEY);
        // 상품마다 정하기로 확정됐다 (V38). 임시값으로 떨어지는 경로가 아니다.
        if (raw.isPresent() && isPerProduct(raw.get())) {
            return false;
        }
        return raw.flatMap(VatMode::parse).isEmpty();
    }

    private static boolean isPerProduct(String settingValue) {
        return PER_PRODUCT.equals(settingValue.trim().toUpperCase());
    }
}
