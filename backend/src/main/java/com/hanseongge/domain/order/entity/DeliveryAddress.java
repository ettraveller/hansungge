package com.hanseongge.domain.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 배송지 주소록 ({@code delivery_addresses}).
 *
 * <p>주문은 받는 사람·주소를 <b>자기 칸에 복사해</b> 간다. 그래서 여기를 고치거나 지워도
 * 지나간 주문의 배송지는 바뀌지 않는다 — 어디로 보냈는지는 분쟁의 근거라 바뀌면 안 된다.
 * 이 표는 "다음에 쓸 주소"만 들고 있는 편의 장치다.
 */
@Entity
@Getter
@Table(name = "delivery_addresses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 별칭("집", "회사"). 목록에서 주소를 다 읽지 않고 고를 수 있게 한다. */
    @Column(name = "label", length = 30)
    private String label;

    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 30)
    private String receiverPhone;

    @Column(name = "zipcode", nullable = false, length = 10)
    private String zipcode;

    @Column(name = "address1", nullable = false, length = 200)
    private String address1;

    @Column(name = "address2", length = 200)
    private String address2;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static DeliveryAddress of(Long userId, String label, String receiverName, String receiverPhone,
                                     String zipcode, String address1, String address2) {
        DeliveryAddress address = new DeliveryAddress();
        address.userId = userId;
        address.createdAt = OffsetDateTime.now();
        address.apply(label, receiverName, receiverPhone, zipcode, address1, address2);
        return address;
    }

    public void apply(String label, String receiverName, String receiverPhone,
                      String zipcode, String address1, String address2) {
        this.label = blankToNull(label);
        this.receiverName = receiverName;
        this.receiverPhone = receiverPhone;
        this.zipcode = zipcode;
        this.address1 = address1;
        this.address2 = blankToNull(address2);
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 기본 배송지 여부.
     *
     * <p>서비스가 "다른 것을 전부 내리고 이것을 올린다" 순서로 부른다. DB 에도 부분 UNIQUE
     * 가 걸려 있어, 두 요청이 겹쳐 둘 다 올라가는 일은 거기서 막힌다.
     */
    public void markDefault(boolean value) {
        if (this.isDefault != value) {
            this.isDefault = value;
            this.updatedAt = OffsetDateTime.now();
        }
    }

    public boolean ownedBy(Long userId) {
        return this.userId.equals(userId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
