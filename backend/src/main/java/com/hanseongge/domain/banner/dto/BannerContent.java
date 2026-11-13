package com.hanseongge.domain.banner.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 화면에서 꾸민 배너의 내용 ({@code hero_banners.content}).
 *
 * <h2>요소를 쌓는다 — 좌표로 놓지 않는다</h2>
 * 배너의 비율이 폭에 따라 뒤집힌다 (PC 1280×560 = 2.3:1, 휴대폰 390×460 = 0.85:1).
 * 좌표로 놓게 하면 PC 에서 오른쪽 끝에 둔 글자가 휴대폰에서는 <b>화면 밖</b>이라,
 * 배너 한 장마다 배치를 두 번 잡아야 한다. 위에서 아래로 쌓으면 지금 히어로 CSS 가
 * 두 폭을 그대로 처리한다 — 반응형이 공짜다.
 *
 * <h2>자유 문자열을 받지 않는다</h2>
 * 이 값은 손님 화면의 {@code style} 로 들어간다. 색·크기·정렬을 글자 그대로 받으면
 * CSS 주입이 된다 ({@code url()} 하나로 외부에 요청이 나간다). 색은 {@code #rrggbb}
 * 하나만, 나머지는 전부 <b>정해진 목록(enum)</b>이다. 모양이 아닌 값은
 * {@code BannerContentValidator} 가 거른다.
 *
 * <h2>나중에 끌어다 놓기를 붙일 자리</h2>
 * 블록에 좌표를 더하고 {@link Mobile} 에 좌표 한 벌을 더 두면 된다. 지금 넣지 않는
 * 이유는 위와 같다 — 쓰는 사람이 배너마다 두 번 잡아야 하기 때문이다.
 */
@Schema(name = "BannerContent", description = "화면에서 꾸민 배너의 내용")
public record BannerContent(

        @Schema(description = "배경")
        Background background,

        @Schema(description = "글자·버튼을 어느 쪽에 붙일지", example = "LEFT")
        Align align,

        @Schema(description = "위에서 아래로 쌓이는 요소들")
        List<Block> blocks,

        @Schema(description = "휴대폰에서만 다르게 할 것. 없으면 PC 와 같다")
        Mobile mobile
) {

    /** 배경. 색 위에 사진을 얹고, 사진 위에 글자가 읽히도록 어둠막을 깐다. */
    @Schema(name = "BannerBackground")
    public record Background(

            @Schema(description = "#rrggbb. 사진이 없으면 이 색만 보인다", example = "#0f1b2d")
            String color,

            @Schema(description = "배경 사진 키. 비우면 색만 쓴다")
            String imageKey,

            @Schema(description = "휴대폰용 배경 사진. 비우면 위 사진을 쓴다")
            String mobileImageKey,

            @Schema(description = "사진 흐림. 0·4·8·16 중 하나", example = "0")
            int blur,

            @Schema(description = "사진 위 어둠막. 글자가 읽히게 한다", example = "GRADIENT")
            Overlay overlay
    ) {
    }

    /** 쌓이는 요소 하나. 글자이거나 버튼이다. */
    @Schema(name = "BannerBlock")
    public record Block(

            @Schema(description = "휴대폰에서 숨길 때 가리키는 이름. 없으면 서버가 붙인다")
            String id,

            @Schema(description = "TEXT 또는 BUTTON")
            BlockType type,

            @Schema(description = "글자의 역할. 크기·굵기가 여기서 정해진다", example = "TITLE")
            TextStyle style,

            @Schema(description = "보일 글자")
            String text,

            @Schema(description = "#rrggbb. 비우면 그 역할의 기본색")
            String color,

            @Schema(description = "BUTTON 이 누르면 갈 곳. 우리 화면 안의 주소만 된다")
            String href,

            @Schema(description = "버튼 모양", example = "GOLD")
            ButtonLook look
    ) {
    }

    /**
     * 휴대폰에서만 다르게 할 것.
     *
     * <p>좁은 화면에서는 설명 한 줄이 배너 절반을 먹는다. 그 줄만 빼고 정렬을 가운데로
     * 바꾸는 것만으로 대부분 해결된다 — 배치를 따로 잡는 것보다 훨씬 적은 일이다.
     */
    @Schema(name = "BannerMobile")
    public record Mobile(

            @Schema(description = "휴대폰에서의 정렬. 비우면 PC 와 같다")
            Align align,

            @Schema(description = "휴대폰에서 숨길 블록의 id")
            List<String> hide
    ) {
    }

    public enum Align { LEFT, CENTER, RIGHT }

    public enum BlockType { TEXT, BUTTON }

    /**
     * 글자의 역할.
     *
     * <p>크기를 px 로 받지 않는다. 받으면 PC 에서 고른 48px 이 휴대폰에서도 48px 이라
     * 제목 한 줄이 배너를 덮는다. 역할만 고르면 두 폭의 크기는 CSS 가 들고 있다 —
     * 지금 기본 배너가 쓰는 바로 그 값이다.
     */
    public enum TextStyle { EYEBROW, TITLE, HIGHLIGHT, DESC }

    public enum ButtonLook { GOLD, OUTLINE, SOLID }

    public enum Overlay { GRADIENT, SOLID, NONE }
}
