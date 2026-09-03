package com.hanseongge.domain.setting.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 운영 설정값 ({@code system_settings}).
 *
 * <p>코드 재배포 없이 바꿔야 하는 값만 담는다.
 * 예: {@code MAX_PRICE_CHANGE_PERCENT}, {@code PRICE_STALE_MINUTES},
 * {@code PRICE_ROUNDING_MODE}(TODO A-2), {@code VAT_MODE}(TODO A-1).
 *
 * <p>V1 마이그레이션이 만든 테이블에는 {@code created_at} 이 없으므로
 * {@code BaseTimeEntity} 를 상속하지 않고 {@code updated_at} 만 직접 관리한다.
 */
@Entity
@Getter
@Table(name = "system_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SystemSetting {

    @Id
    @Column(name = "key", length = 80, nullable = false)
    private String key;

    @Column(name = "value", nullable = false, columnDefinition = "text")
    private String value;

    @Column(name = "description", length = 300)
    private String description;

    @Column(name = "updated_by", length = 60)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private SystemSetting(String key, String value, String description, String updatedBy) {
        this.key = key;
        this.value = value;
        this.description = description;
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }

    public static SystemSetting create(String key, String value, String description, String updatedBy) {
        return new SystemSetting(key, value, description, updatedBy);
    }

    /** 값 변경. 누가 언제 바꿨는지가 항상 함께 남는다. */
    public void update(String newValue, String updatedBy) {
        this.value = newValue;
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }
}
