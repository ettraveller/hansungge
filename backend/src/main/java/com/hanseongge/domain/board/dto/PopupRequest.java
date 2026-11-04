package com.hanseongge.domain.board.dto;

import com.hanseongge.domain.board.entity.PopupKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** 팝업 등록·수정. */
@Schema(name = "PopupRequest", description = "팝업 등록/수정")
public record PopupRequest(

        @Schema(description = "관리자용 구분 이름. 고객에게 보이지 않는다", example = "매입 계산기 안내")
        @NotBlank(message = "팝업 이름을 입력해 주세요.")
        @Size(max = 100)
        String name,

        @Schema(description = "CALCULATOR(매입 계산기) / CONTENT(직접 작성)")
        @NotNull(message = "팝업 종류를 골라 주세요.")
        PopupKind kind,

        @Size(max = 200, message = "제목은 200자까지 입력할 수 있습니다.")
        String title,

        String body,

        @Size(max = 300)
        String imageKey,

        @Size(max = 500)
        String linkHref,

        @Size(max = 60)
        String linkLabel,

        @Schema(description = "노출 시작. 비우면 지금부터")
        OffsetDateTime startsAt,

        @Schema(description = "노출 종료. 비우면 끌 때까지 계속")
        OffsetDateTime endsAt,

        boolean active,

        @Schema(description = "여러 개가 뜰 때의 차례. 작을수록 먼저", example = "0")
        int sort,

        @Schema(description = "「오늘 하루 보지 않기」 제공 여부", example = "true")
        boolean dismissible
) {

    /**
     * 직접 작성이면 제목이 있어야 한다.
     *
     * <p>DB 에도 같은 제약이 있지만(ck_popups_content), 거기까지 가면 500 으로 떨어져
     * 관리자가 무엇이 잘못됐는지 모른다. 여기서 막아 문장으로 알려 준다.
     */
    @AssertTrue(message = "직접 작성 팝업은 제목이 필요합니다.")
    public boolean isTitlePresentForContent() {
        return kind != PopupKind.CONTENT || (title != null && !title.isBlank());
    }

    /** 주소 없는 버튼은 눌러도 아무 일이 없다. 글자만 적어 두는 실수를 막는다. */
    @AssertTrue(message = "버튼 글자를 적으려면 연결할 주소도 넣어 주세요.")
    public boolean isLinkHrefPresentWhenLabelled() {
        return linkLabel == null || linkLabel.isBlank() || (linkHref != null && !linkHref.isBlank());
    }

    /** 거꾸로 넣으면 영영 안 뜬다. 화면에는 아무 일도 없어 한참을 헤맨다. */
    @AssertTrue(message = "노출 종료가 시작보다 앞섭니다. 기간을 다시 확인해 주세요.")
    public boolean isPeriodInOrder() {
        return startsAt == null || endsAt == null || startsAt.isBefore(endsAt);
    }
}
