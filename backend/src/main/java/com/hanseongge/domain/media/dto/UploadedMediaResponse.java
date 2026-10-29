package com.hanseongge.domain.media.dto;

import com.hanseongge.domain.media.MediaStorage;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 업로드 결과.
 *
 * <p>키와 URL 을 함께 준다. 키는 저장용(상품에 연결할 값), URL 은 방금 올린 사진을
 * 그 자리에서 미리보기로 띄우는 용도다. URL 을 돌려주지 않으면 화면이 주소를 조립해야
 * 하고, 그러면 저장 위치를 바꿀 때 고쳐야 할 곳이 둘로 늘어난다.
 */
@Schema(description = "업로드된 이미지")
public record UploadedMediaResponse(
        @Schema(description = "DB 에 저장할 키", example = "products/2026/10/3f2a1b4c.webp")
        String objectKey,
        @Schema(description = "브라우저가 읽는 주소")
        String url
) {
    public static UploadedMediaResponse from(MediaStorage.StoredMedia stored) {
        return new UploadedMediaResponse(stored.objectKey(), stored.url());
    }
}
