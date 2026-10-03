package com.hanseongge.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "DeliveryAddressRequest", description = "배송지 등록·수정")
public record DeliveryAddressRequest(
        @Schema(description = "별칭. 비워도 된다", example = "집")
        @Size(max = 30)
        String label,

        @Schema(description = "받는 분", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "받는 분을 입력해 주세요.")
        @Size(max = 100)
        String receiverName,

        @Schema(description = "연락처", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "연락처를 입력해 주세요.")
        @Size(max = 30)
        String receiverPhone,

        @Schema(description = "우편번호", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "우편번호를 입력해 주세요.")
        @Size(max = 10)
        String zipcode,

        @Schema(description = "주소", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "주소를 입력해 주세요.")
        @Size(max = 200)
        String address1,

        @Schema(description = "상세 주소. 비워도 된다")
        @Size(max = 200)
        String address2,

        @Schema(description = "기본 배송지로 둘 것인가. 첫 배송지는 값과 무관하게 기본이 된다")
        boolean setDefault
) {}
