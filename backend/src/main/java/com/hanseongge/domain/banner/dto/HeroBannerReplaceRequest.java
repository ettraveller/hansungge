package com.hanseongge.domain.banner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 메인 배너 전체 교체.
 *
 * <p>한 장씩 더하고 지우지 않는다. 배너는 <b>묶음으로</b> 바뀌는 것이라 — 봄 배너를
 * 여름 배너로 갈아 끼울 때 세 장을 각각 지우고 세 장을 각각 넣게 하면, 그 사이에
 * 반쯤 섞인 배너가 손님 화면에 걸린다.
 *
 * <p>보낸 차례가 그대로 슬라이드 차례가 된다.
 */
@Schema(name = "HeroBannerReplaceRequest", description = "메인 배너 전체 교체")
public record HeroBannerReplaceRequest(
        @Schema(description = "배너 목록. 보낸 차례대로 나간다. 빈 배열이면 배너를 모두 내린다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "배너 목록이 필요합니다.")
        @Size(max = 10, message = "배너는 10장까지 넣을 수 있습니다.")
        @Valid
        List<Item> banners
) {

    @Schema(name = "HeroBannerItem")
    public record Item(
            @Schema(description = "업로드한 이미지 키", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank(message = "이미지가 필요합니다.")
            @Size(max = 300)
            String imageKey,

            @Schema(description = "누르면 갈 곳 (예: /shop?categoryId=1). 비우면 누를 수 없다")
            @Size(max = 500)
            String linkHref,

            @Schema(description = "화면 낭독기가 읽을 설명")
            @Size(max = 200)
            String altText
    ) {}
}
