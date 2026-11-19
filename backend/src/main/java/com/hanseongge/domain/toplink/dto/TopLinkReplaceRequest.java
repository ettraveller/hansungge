package com.hanseongge.domain.toplink.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 상단 띠 링크 전체 교체.
 *
 * <p>메인 배너와 같은 방식이다 — 보낸 것이 곧 전체이고, 보낸 차례가 띠에 나가는
 * 차례다. 한 개씩 더하고 지우는 길을 두면 순서를 바꾸는 요청이 따로 필요해지고,
 * 그 사이에 반쯤 바뀐 띠가 모든 화면 맨 위에 걸린다.
 *
 * <p>칸의 길이·모양은 여기서 막지 않고 {@code TopLinkService} 가 본다. 여기서 막으면
 * 「3번째 링크의 주소가 틀렸다」 대신 필드 경로({@code links[2].href})가 나간다.
 */
@Schema(name = "TopLinkReplaceRequest", description = "상단 띠 링크 전체 교체")
public record TopLinkReplaceRequest(
        @Schema(description = "링크 목록. 보낸 차례대로 나간다. 빈 배열이면 링크를 모두 내린다",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "링크 목록이 필요합니다.")
        @Size(max = MAX_LINKS, message = "링크는 " + MAX_LINKS + "개까지 넣을 수 있습니다.")
        @Valid
        List<Item> links
) {

    /**
     * 띠 한 줄에 넣을 수 있는 개수.
     *
     * <p>띠의 높이는 한 줄(34px)이다. 그보다 많이 걸면 좁은 화면에서 넘친 것이 잘리거나
     * 옆으로 밀려, 걸어 둔 링크가 손님에게 보이지 않는다.
     */
    public static final int MAX_LINKS = 12;

    @Schema(name = "TopLinkItem")
    public record Item(
            @Schema(description = "이름. 아이콘이 있어도 적는다 — 화면 낭독기가 읽는다") String label,
            @Schema(description = "누르면 갈 곳. https:// 로 시작하는 주소이거나 /shop 같은 사이트 안 경로")
            String href,
            @Schema(description = "올린 아이콘의 미디어 키. 비우면 글자 링크") String iconKey
    ) {}
}
