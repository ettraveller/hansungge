package com.hanseongge.domain.product.controller;

import com.hanseongge.domain.product.dto.CategoryRequest;
import com.hanseongge.domain.product.dto.CategoryResponse;
import com.hanseongge.domain.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 카테고리 관리 API (설계서 §5 — {@code CRUD /api/admin/categories}).
 *
 * <p>조회는 {@code OPERATOR} 이상, 변경은 {@code MANAGER} 이상이다 (설계서 §7.4 —
 * MANAGER 범위에 상품·콘텐츠가 포함된다).
 */
@Tag(name = "관리자 카테고리", description = "카테고리 CRUD")
@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATOR')")
public class AdminCategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "카테고리 목록", description = "비활성 포함 전체.")
    @GetMapping
    public ResponseEntity<List<CategoryResponse>> list() {
        return ResponseEntity.ok(categoryService.findAll().stream().map(CategoryResponse::from).toList());
    }

    @Operation(summary = "카테고리 등록")
    @PreAuthorize("hasRole('MANAGER')")
    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponse.from(
                categoryService.create(request.parentId(), request.name(), request.sort())));
    }

    @Operation(summary = "카테고리 수정",
            description = "자기 자신이나 하위 카테고리를 부모로 지정할 수 없다(순환 참조 방지).")
    @PreAuthorize("hasRole('MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(CategoryResponse.from(categoryService.update(
                id, request.parentId(), request.name(), request.sort(), request.activeOrDefault())));
    }

    @Operation(summary = "카테고리 삭제",
            description = "상품이나 하위 카테고리가 있으면 409 다. 그 경우 active=false 로 숨긴다.")
    @PreAuthorize("hasRole('MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
