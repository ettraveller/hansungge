package com.hanseongge.domain.board.controller;

import com.hanseongge.domain.board.dto.AdminEventResponse;
import com.hanseongge.domain.board.dto.EventEntryResponse;
import com.hanseongge.domain.board.dto.EventRequest;
import com.hanseongge.domain.board.entity.Event;
import com.hanseongge.domain.board.entity.EventEntry;
import com.hanseongge.domain.board.service.EventService;
import com.hanseongge.global.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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

import java.util.Map;

/** 이벤트 관리. */
@Tag(name = "관리자 이벤트", description = "이벤트 등록 · 수정 · 내리기 · 응모 확인")
@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
@PreAuthorize("hasRole('OPERATOR')")
public class AdminEventController {

    private final EventService eventService;

    @Operation(summary = "이벤트 목록", description = "내린 이벤트도 보인다.")
    @GetMapping
    public ResponseEntity<PageResponse<AdminEventResponse>> list(
            @PageableDefault(size = 20) Pageable pageable) {

        Page<Event> page = eventService.findAdminPage(pageable);
        Map<Long, Long> counts = eventService.countEntriesByEvents(page.getContent());

        return ResponseEntity.ok(PageResponse.of(page, event ->
                AdminEventResponse.of(event, counts.getOrDefault(event.getId(), 0L))));
    }

    @Operation(summary = "이벤트 상세")
    @GetMapping("/{id}")
    public ResponseEntity<AdminEventResponse> detail(@PathVariable Long id) {
        Event event = eventService.find(id);
        return ResponseEntity.ok(AdminEventResponse.of(event, eventService.countEntries(id)));
    }

    /**
     * 응모 목록 (관리자).
     *
     * <p>{@code admin=true} 로 넘겨 <b>비밀 응모글의 내용까지</b> 본다. 당첨자를 뽑으려면
     * 응모 내용을 읽어야 하고, 그것이 이 화면의 목적이다.
     */
    @Operation(summary = "응모 목록", description = "비밀 응모글의 내용도 보인다.")
    @GetMapping("/{id}/entries")
    public ResponseEntity<PageResponse<EventEntryResponse>> entries(
            @PathVariable Long id,
            @PageableDefault(size = 50) Pageable pageable) {

        Page<EventEntry> page = eventService.findEntries(id, pageable);
        Map<Long, String> names = eventService.maskedEntrantNames(page.getContent());

        return ResponseEntity.ok(PageResponse.of(page, entry ->
                EventEntryResponse.of(entry, names.getOrDefault(entry.getUserId(), "회원"),
                        null, true)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "이벤트 등록")
    @PostMapping
    public ResponseEntity<AdminEventResponse> create(@Valid @RequestBody EventRequest request) {
        Event event = eventService.create(request.title(), request.content(),
                request.thumbnailKey(), request.startAt(), request.endAt());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdminEventResponse.of(event, 0L));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "이벤트 수정")
    @PutMapping("/{id}")
    public ResponseEntity<AdminEventResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody EventRequest request) {
        Event event = eventService.update(id, request.title(), request.content(),
                request.thumbnailKey(), request.startAt(), request.endAt(),
                request.activeOrDefault());
        return ResponseEntity.ok(AdminEventResponse.of(event, eventService.countEntries(id)));
    }

    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "이벤트 내리기", description = "행을 삭제하지 않고 노출만 중단한다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> hide(@PathVariable Long id) {
        eventService.hide(id);
        return ResponseEntity.noContent().build();
    }
}
