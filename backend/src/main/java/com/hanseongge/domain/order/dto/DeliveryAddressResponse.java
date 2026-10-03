package com.hanseongge.domain.order.dto;

import com.hanseongge.domain.order.entity.DeliveryAddress;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DeliveryAddressResponse", description = "저장된 배송지")
public record DeliveryAddressResponse(
        Long id,
        @Schema(description = "별칭. 없으면 null", example = "집") String label,
        String receiverName,
        String receiverPhone,
        String zipcode,
        String address1,
        String address2,
        @Schema(description = "기본 배송지인가") boolean isDefault
) {
    public static DeliveryAddressResponse from(DeliveryAddress a) {
        return new DeliveryAddressResponse(a.getId(), a.getLabel(), a.getReceiverName(), a.getReceiverPhone(),
                a.getZipcode(), a.getAddress1(), a.getAddress2(), a.isDefault());
    }
}
