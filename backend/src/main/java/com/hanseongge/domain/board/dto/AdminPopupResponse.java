package com.hanseongge.domain.board.dto;

import com.hanseongge.domain.board.entity.Popup;
import com.hanseongge.domain.board.entity.PopupKind;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** 관리자 목록·상세. 고객 응답과 달리 기간·끄고켬·작성자까지 보여준다. */
@Schema(name = "AdminPopupResponse", description = "팝업 (관리자)")
public record AdminPopupResponse(

        Long id,
        @Schema(description = "관리자용 구분 이름") String name,
        PopupKind kind,
        String title,
        String body,
        String imageKey,
        String linkHref,
        String linkLabel,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        boolean active,
        int sort,
        boolean dismissible,

        /**
         * 지금 이 순간 고객 화면에 떠 있는가.
         *
         * <p>켬/끔과 기간을 머릿속으로 합쳐 보게 두지 않는다 — 기간을 거꾸로 넣거나
         * 어제로 끝난 것을 켜 둔 채 "왜 안 뜨지" 하는 일이 실제로 생긴다.
         */
        @Schema(description = "지금 떠 있는가", example = "true") boolean live,

        OffsetDateTime updatedAt,
        String updatedBy
) {

    public static AdminPopupResponse from(Popup popup, OffsetDateTime now) {
        return new AdminPopupResponse(
                popup.getId(), popup.getName(), popup.getKind(),
                popup.getTitle(), popup.getBody(), popup.getImageKey(),
                popup.getLinkHref(), popup.getLinkLabel(),
                popup.getStartsAt(), popup.getEndsAt(),
                popup.isActive(), popup.getSort(), popup.isDismissible(),
                popup.isLiveAt(now),
                popup.getUpdatedAt(), popup.getUpdatedBy());
    }
}
