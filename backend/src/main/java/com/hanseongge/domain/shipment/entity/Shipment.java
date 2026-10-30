package com.hanseongge.domain.shipment.entity;

import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 배송 ({@code shipments}).
 *
 * <p>주문 하나에 하나다 (V32 의 {@code ux_shipment_order}). 부분 발송은 열지 않았다 —
 * 귀금속은 한 번에 보내고, 나눠 보내려면 어느 품목이 어느 송장에 실렸는지를 품목마다
 * 적어야 한다.
 *
 * <h2>송장번호는 숫자만 저장한다</h2>
 * 사람이 옮겨 적으면 하이픈·공백이 섞인다. 그대로 두면 같은 번호가 두 모양으로 저장되어
 * 중복 검사도, 택배사 조회도 어긋난다. 들어올 때 한 번 걸러 숫자만 남긴다.
 *
 * <h2>배송조회 결과는 여기 담아 둔다</h2>
 * 화면을 열 때마다 외부를 부르면 요청이 그만큼 나가고(비용·지연), 택배사가 느릴 때
 * 우리 화면도 같이 느려진다. 받아 온 것을 {@code trackingDetail} 에 두고 화면은
 * 그것만 읽는다.
 */
@Entity
@Getter
@Table(name = "shipments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "carrier", length = 40)
    private Carrier carrier;

    @Column(name = "tracking_no", length = 60)
    private String trackingNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private ShipmentStatus status;

    @Column(name = "shipped_at")
    private OffsetDateTime shippedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;

    @Column(name = "tracking_synced_at")
    private OffsetDateTime trackingSyncedAt;

    @Column(name = "tracking_status", length = 40)
    private String trackingStatus;

    /** 택배사가 준 진행 단계 목록(JSON). 화면이 그대로 그린다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tracking_detail")
    private String trackingDetail;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private Shipment(Long orderId) {
        this.orderId = orderId;
        this.status = ShipmentStatus.PENDING;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 아직 송장번호가 없는 상태로 만든다. 결제가 끝나면 주문마다 하나씩 생긴다. */
    public static Shipment pending(Long orderId) {
        return new Shipment(orderId);
    }

    /**
     * 송장번호를 넣고 발송으로 바꾼다.
     *
     * <p>이미 발송된 건에도 다시 쓸 수 있다 — 번호를 잘못 넣어 고치는 일이 실제로
     * 생기고, 그때 상태를 되감게 하는 것보다 번호만 바꾸는 쪽이 안전하다.
     * 발송 시각은 처음 넣을 때만 기록한다. 고치면서 시각까지 바뀌면 "언제 보냈나"가
     * 사라진다.
     */
    public void ship(Carrier carrier, String rawTrackingNo) {
        if (status == ShipmentStatus.DELIVERED) {
            throw new BusinessException(ErrorCode.SHIPMENT_ALREADY_DELIVERED);
        }
        String digits = digitsOf(rawTrackingNo);
        if (!carrier.isValidTrackingNo(digits)) {
            throw new BusinessException(ErrorCode.SHIPMENT_TRACKING_NO_INVALID,
                    "자릿수 불일치 carrier=" + carrier + " length="
                            + (digits == null ? 0 : digits.length()));
        }

        this.carrier = carrier;
        this.trackingNo = digits;
        if (this.shippedAt == null) {
            this.shippedAt = OffsetDateTime.now();
        }
        // 번호가 바뀌었으면 예전 조회 결과는 다른 물건의 것이다. 함께 비운다.
        this.trackingSyncedAt = null;
        this.trackingStatus = null;
        this.trackingDetail = null;
        this.status = ShipmentStatus.SHIPPED;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 수령 기록. 이미 완료된 건에 다시 불러도 시각을 덮어쓰지 않는다. */
    public void markDelivered() {
        if (status == ShipmentStatus.PENDING) {
            throw new BusinessException(ErrorCode.SHIPMENT_NOT_SHIPPED);
        }
        if (this.deliveredAt == null) {
            this.deliveredAt = OffsetDateTime.now();
        }
        this.status = ShipmentStatus.DELIVERED;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 배송조회 결과를 적어 둔다.
     *
     * <p>택배사가 "배송완료"라고 해도 {@link #deliveredAt} 을 여기서 쓰지 않는다 —
     * 수령 확인은 사람이 하는 기록이고, 외부 응답 한 번으로 그것을 대신하면 분쟁에서
     * 근거가 약해진다. 상태만 올려 두고 완료 처리는 관리자가 한다.
     */
    public void applyTracking(String externalStatus, String detailJson, boolean inTransit) {
        this.trackingStatus = externalStatus;
        this.trackingDetail = detailJson;
        this.trackingSyncedAt = OffsetDateTime.now();
        if (inTransit && status == ShipmentStatus.SHIPPED) {
            this.status = ShipmentStatus.IN_TRANSIT;
        }
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean hasTrackingNo() {
        return trackingNo != null && !trackingNo.isBlank();
    }

    /** 하이픈·공백·괄호를 걷어내고 숫자만 남긴다. */
    private static String digitsOf(String raw) {
        return raw == null ? null : raw.replaceAll("[^0-9]", "");
    }
}
