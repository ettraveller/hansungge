package com.hanseongge.global.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 목록 응답 공통 형태.
 *
 * <p>Spring Data 의 {@code Page} 를 그대로 직렬화하지 않는다. {@code Pageable} 내부 구조가
 * 통째로 응답에 실려 나가고, 그 모양은 Spring 버전에 따라 바뀐다 — 프론트엔드와의 계약을
 * 프레임워크 내부 구조에 묶어두면 업그레이드가 곧 API 변경이 된다.
 */
@Schema(name = "PageResponse", description = "페이지 목록 응답")
public record PageResponse<T>(

        @Schema(description = "현재 페이지 내용")
        List<T> content,

        @Schema(description = "현재 페이지 번호 (0부터)", example = "0")
        int page,

        @Schema(description = "페이지 크기", example = "20")
        int size,

        @Schema(description = "전체 건수", example = "137")
        long totalElements,

        @Schema(description = "전체 페이지 수", example = "7")
        int totalPages,

        @Schema(description = "마지막 페이지 여부", example = "false")
        boolean last
) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
