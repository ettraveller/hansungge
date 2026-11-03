package com.hanseongge.domain.setting.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 무통장입금 받을 계좌 설정.
 *
 * <h2>왜 시스템 설정 전체를 열지 않는가</h2>
 * {@code system_settings} 에는 {@code MAX_PRICE_CHANGE_PERCENT}(시세 자동적용 허용
 * 변동률)처럼 잘못 건드리면 값이 틀어지는 값들이 섞여 있다. 계좌를 넣으려고 그 표를
 * 통째로 편집 가능하게 만들면, 계좌를 고치러 들어온 사람이 시세 안전망을 끌 수 있게 된다.
 *
 * <h2>빈 값을 허용한다</h2>
 * 셋 중 하나라도 비면 서비스가 「계좌 없음」으로 보고 무통장 주문을 받지 않는다
 * ({@code OrderService#bankAccount}). 계좌를 내리는 길이 그것이다 — 지우는 버튼을
 * 따로 두지 않는다.
 */
@Schema(name = "BankAccountSettingRequest", description = "무통장입금 계좌 설정")
public record BankAccountSettingRequest(

        @Schema(description = "은행명", example = "국민은행")
        @Size(max = 40, message = "은행명은 40자까지 입력할 수 있습니다.")
        String bank,

        @Schema(description = "계좌번호", example = "123456-78-901234")
        @Size(max = 40, message = "계좌번호는 40자까지 입력할 수 있습니다.")
        String number,

        @Schema(description = "예금주", example = "주식회사 금한성")
        @Size(max = 60, message = "예금주는 60자까지 입력할 수 있습니다.")
        String holder,

        /**
         * 입금 안내 기한(시간).
         *
         * <p>안내용이다. 지나도 주문이 취소되지 않는다 — 입금하려고 계좌를 열어 둔
         * 사이에 주문이 사라지면 보낸 돈이 갈 곳을 잃는다.
         */
        @Schema(description = "입금 안내 기한(시간). 자동취소하지 않는다", example = "3")
        Integer depositDueHours
) {
}
