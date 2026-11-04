package com.hanseongge.domain.board.service;

import com.hanseongge.domain.board.dto.AdminPopupResponse;
import com.hanseongge.domain.board.dto.PopupRequest;
import com.hanseongge.domain.board.dto.PopupResponse;
import com.hanseongge.domain.board.entity.Popup;
import com.hanseongge.domain.board.repository.PopupRepository;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 메인 팝업.
 *
 * <h2>기간 판정을 질의에 넣지 않는다</h2>
 * 켜진 것을 전부 읽고 {@code Popup#isLiveAt} 하나가 가린다. 팝업은 많아야 서너 개라
 * 전부 읽어도 비용이 없고, 조건이 SQL 과 코드 두 곳에 갈라지면 "왜 안 뜨지"를 두
 * 군데서 찾게 된다. 관리자 화면이 보여 주는 「지금 떠 있는가」도 같은 판정을 쓴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PopupService {

    private final PopupRepository popupRepository;

    /** 고객 화면이 지금 띄울 것. 없으면 빈 목록이다 — 팝업이 없는 것은 정상이다. */
    @Transactional(readOnly = true)
    public List<PopupResponse> live() {
        OffsetDateTime now = OffsetDateTime.now();
        return popupRepository.findByActiveTrueOrderBySortAscIdAsc().stream()
                .filter(popup -> popup.isLiveAt(now))
                .map(PopupResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminPopupResponse> listForAdmin() {
        OffsetDateTime now = OffsetDateTime.now();
        return popupRepository.findAllByOrderBySortAscIdAsc().stream()
                .map(popup -> AdminPopupResponse.from(popup, now))
                .toList();
    }

    @Transactional
    public AdminPopupResponse create(PopupRequest request, String actor) {
        Popup popup = popupRepository.save(Popup.create(
                request.name().trim(), request.kind(),
                blankToNull(request.title()), blankToNull(request.body()),
                blankToNull(request.imageKey()),
                blankToNull(request.linkHref()), blankToNull(request.linkLabel()),
                request.startsAt(), request.endsAt(),
                request.active(), request.sort(), request.dismissible(), actor));

        log.info("[POPUP] 등록 id={} name={} kind={} by={}",
                popup.getId(), popup.getName(), popup.getKind(), actor);
        return AdminPopupResponse.from(popup, OffsetDateTime.now());
    }

    @Transactional
    public AdminPopupResponse update(Long id, PopupRequest request, String actor) {
        Popup popup = require(id);
        popup.update(
                request.name().trim(), request.kind(),
                blankToNull(request.title()), blankToNull(request.body()),
                blankToNull(request.imageKey()),
                blankToNull(request.linkHref()), blankToNull(request.linkLabel()),
                request.startsAt(), request.endsAt(),
                request.active(), request.sort(), request.dismissible(), actor);

        log.info("[POPUP] 수정 id={} name={} by={}", id, popup.getName(), actor);
        return AdminPopupResponse.from(popup, OffsetDateTime.now());
    }

    @Transactional
    public AdminPopupResponse changeActive(Long id, boolean active, String actor) {
        Popup popup = require(id);
        popup.changeActive(active, actor);
        log.info("[POPUP] {} id={} by={}", active ? "켬" : "끔", id, actor);
        return AdminPopupResponse.from(popup, OffsetDateTime.now());
    }

    /**
     * 지운다.
     *
     * <p>주문·게시글과 달리 지워도 된다 — 거래 기록이 아니라 한동안 띄웠던 알림창이고,
     * 남겨 둘 법적 의무가 없다. 다만 끄는 것으로 충분한 경우가 대부분이라 관리자
     * 화면은 「끄기」를 먼저 권한다.
     */
    @Transactional
    public void delete(Long id, String actor) {
        Popup popup = require(id);
        popupRepository.delete(popup);
        log.info("[POPUP] 삭제 id={} name={} by={}", id, popup.getName(), actor);
    }

    private Popup require(Long id) {
        return popupRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "팝업 없음: " + id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
