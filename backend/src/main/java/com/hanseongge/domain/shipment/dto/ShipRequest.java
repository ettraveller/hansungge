package com.hanseongge.domain.shipment.dto;

import com.hanseongge.domain.shipment.entity.Carrier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 송장번호 입력.
 *
 * <p>자릿수 검사는 여기서 하지 않는다. 하이픈·공백을 걷어낸 뒤에 세야 하는데 그 일은
 * {@code Shipment.ship} 이 한다 — 두 곳에서 세면 한쪽만 고치는 날 규칙이 갈린다.
 */
@Schema(name = "ShipRequest", description = "송장번호 입력")
public record ShipRequest(

        @Schema(description = "택배사. 지금은 KOREA_POST 하나뿐이라 비우면 그것으로 본다",
                example = "KOREA_POST")
        Carrier carrier,

        @Schema(description = "송장번호. 하이픈·공백이 섞여도 된다 — 서버가 숫자만 남긴다",
                example = "6012345678901", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "송장번호를 입력해 주세요.")
        @Size(max = 60)
        String trackingNo
) {

    public ShipRequest {
        // 택배사가 하나뿐인 동안 화면이 매번 보내게 하지 않는다. 늘어나면 필수로 바꾼다.
        carrier = carrier == null ? Carrier.KOREA_POST : carrier;
    }
}
