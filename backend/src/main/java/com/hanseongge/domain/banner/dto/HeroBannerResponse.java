package com.hanseongge.domain.banner.dto;

import com.hanseongge.domain.banner.entity.BannerKind;
import com.hanseongge.domain.banner.entity.HeroBanner;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "HeroBannerResponse", description = "메인 배너 한 장")
public record HeroBannerResponse(
        @Schema(description = "배너 ID") Long id,
        @Schema(description = "IMAGE=사진 한 장, DESIGN=화면에서 꾸민 배너") BannerKind kind,
        @Schema(description = "꾸민 배너의 내용. IMAGE 면 없다") BannerContent content,
        @Schema(description = "이미지 키. 화면이 미디어 주소로 바꿔 쓴다") String imageKey,
        @Schema(description = "휴대폰용 이미지 키. 없으면 imageKey 를 쓴다") String mobileImageKey,
        @Schema(description = "누르면 갈 곳. 없으면 누를 수 없다") String linkHref,
        @Schema(description = "화면 낭독기가 읽을 설명") String altText,
        @Schema(description = "진열 차례") int sort,
        @Schema(description = "내걸려 있는가") boolean active
) {
    public static HeroBannerResponse from(HeroBanner banner, BannerContent content) {
        return new HeroBannerResponse(banner.getId(), banner.getKind(), content,
                banner.getImageKey(), banner.getMobileImageKey(), banner.getLinkHref(),
                banner.getAltText(), banner.getSort(), banner.isActive());
    }
}
