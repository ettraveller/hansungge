package com.hanseongge.domain.board.dto;

import com.hanseongge.domain.board.entity.Popup;
import com.hanseongge.domain.board.entity.PopupKind;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 고객 화면이 받는 팝업. <b>관리자용 이름과 기간은 담지 않는다</b> —
 * 고객이 알 필요가 없고, 내보내 봐야 운영 사정만 드러난다.
 */
@Schema(name = "PopupResponse", description = "메인 팝업")
public record PopupResponse(

        @Schema(description = "팝업 ID. 「오늘 하루 보지 않기」를 이 값으로 기억한다", example = "1")
        Long id,

        @Schema(description = "CALCULATOR(매입 계산기) / CONTENT(직접 작성)")
        PopupKind kind,

        @Schema(description = "제목. CALCULATOR 면 null") String title,
        @Schema(description = "본문. CALCULATOR 면 null") String body,
        @Schema(description = "이미지 Object Storage 키. 없으면 null") String imageKey,
        @Schema(description = "누르면 갈 곳. 없으면 버튼을 그리지 않는다") String linkHref,
        @Schema(description = "버튼 글자") String linkLabel,

        @Schema(description = "「오늘 하루 보지 않기」를 띄울지", example = "true")
        boolean dismissible
) {

    public static PopupResponse from(Popup popup) {
        return new PopupResponse(
                popup.getId(), popup.getKind(),
                popup.getTitle(), popup.getBody(), popup.getImageKey(),
                popup.getLinkHref(), popup.getLinkLabel(), popup.isDismissible());
    }
}
