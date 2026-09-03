package com.hanseongge.domain.board.controller;

import com.hanseongge.domain.board.dto.EventDetailResponse;
import com.hanseongge.domain.board.dto.EventEntryRequest;
import com.hanseongge.domain.board.dto.EventEntryResponse;
import com.hanseongge.domain.board.dto.EventSummaryResponse;
import com.hanseongge.domain.board.entity.Event;
import com.hanseongge.domain.board.entity.EventEntry;
import com.hanseongge.domain.board.service.EventService;
import com.hanseongge.global.auth.UserPrincipal;
import com.hanseongge.global.dto.PageResponse;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 이벤트 (공개 + 회원 응모). */
@Tag(name = "이벤트", description = "이벤트 목록/상세와 응모")
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class PublicEventController {

    private final EventService eventService;

    @Operation(summary = "이벤트 목록",
               description = "종료된 이벤트도 남는다. 진행 상태는 기간으로 계산해 내려준다.")
    @GetMapping
    public ResponseEntity<PageResponse<EventSummaryResponse>> list(
            @PageableDefault(size = 12) Pageable pageable) {

        Page<Event> page = eventService.findPublicPage(pageable);
        Map<Long, Long> counts = eventService.countEntriesByEvents(page.getContent());

        return ResponseEntity.ok(PageResponse.of(page, event ->
                EventSummaryResponse.of(event, counts.getOrDefault(event.getId(), 0L))));
    }

    @Operation(summary = "이벤트 상세")
    @GetMapping("/{id}")
    public ResponseEntity<EventDetailResponse> detail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal) {

        Long viewerId = principal == null ? null : principal.id();
        eventService.increaseViewCount(id);

        Event event = eventService.findPublic(id);
        return ResponseEntity.ok(EventDetailResponse.of(
                event,
                eventService.countEntries(id),
                eventService.hasEntered(id, viewerId)));
    }

    @Operation(summary = "응모 목록",
               description = "비밀 응모글은 자물쇠만 보이고 내용은 비어서 나간다.")
    @GetMapping("/{id}/entries")
    public ResponseEntity<PageResponse<EventEntryResponse>> entries(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {

        Long viewerId = principal == null ? null : principal.id();
        Page<EventEntry> page = eventService.findEntries(id, pageable);
        Map<Long, String> names = eventService.maskedEntrantNames(page.getContent());

        return ResponseEntity.ok(PageResponse.of(page, entry -> EventEntryResponse.of(
                entry, names.getOrDefault(entry.getUserId(), "회원"), viewerId, false)));
    }

    @Operation(summary = "이벤트 응모",
               description = "로그인이 필요하다. 기간 밖이거나 이미 응모했으면 409 다.")
    @PostMapping("/{id}/entries")
    public ResponseEntity<EventEntryResponse> enter(
            @PathVariable Long id,
            @Valid @RequestBody EventEntryRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        requireLogin(principal);

        EventEntry entry = eventService.enter(id, principal.id(),
                request.content(), request.secretOrDefault());

        Map<Long, String> names = eventService.maskedEntrantNames(List.of(entry));
        return ResponseEntity.status(HttpStatus.CREATED).body(EventEntryResponse.of(
                entry, names.getOrDefault(entry.getUserId(), "회원"), principal.id(), false));
    }

    @Operation(summary = "응모 취소", description = "본인 응모만 취소할 수 있다.")
    @DeleteMapping("/entries/{entryId}")
    public ResponseEntity<Void> withdrawEntry(@PathVariable Long entryId,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        requireLogin(principal);
        eventService.withdrawEntry(entryId, principal.id());
        return ResponseEntity.noContent().build();
    }

    private void requireLogin(UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "비로그인 이벤트 응모 시도");
        }
    }
}
