package com.hanseongge.domain.toplink.dto;

import com.hanseongge.domain.toplink.entity.TopLink;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TopLinkResponse", description = "상단 띠의 링크 하나")
public record TopLinkResponse(
        @Schema(description = "링크 ID") Long id,
        @Schema(description = "글자 링크의 글자. 아이콘 링크면 화면 낭독기가 읽는 이름") String label,
        @Schema(description = "누르면 갈 곳") String href,
        @Schema(description = "아이콘 미디어 키. 없으면 글자 링크다") String iconKey
) {
    public static TopLinkResponse from(TopLink link) {
        return new TopLinkResponse(link.getId(), link.getLabel(), link.getHref(), link.getIconKey());
    }
}
