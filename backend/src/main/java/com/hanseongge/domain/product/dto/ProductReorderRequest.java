package com.hanseongge.domain.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 진열 순서 저장.
 *
 * <p>보낸 차례가 그대로 진열 순서가 된다. 자리 번호를 따로 보내지 않는 이유는, 번호를
 * 화면이 매기면 빠진 번호나 겹친 번호가 섞일 수 있고 그것을 서버가 다시 풀어야 하기
 * 때문이다. 차례만 받으면 서버가 0 부터 새로 매긴다.
 *
 * <p><b>전체를 보낸다.</b> 「골드바」 탭에서 끌었더라도 화면이 들고 있는 전체 순서를
 * 보낸다 — 일부만 보내면 보내지 않은 상품과 자리 번호가 겹친다.
 */
@Schema(name = "ProductReorderRequest", description = "진열 순서 저장")
public record ProductReorderRequest(
        @Schema(description = "진열할 차례대로의 상품 ID 전체", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "진열 순서가 비어 있습니다.")
        List<Long> productIds
) {}
