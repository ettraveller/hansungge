package com.hanseongge.domain.board.repository;

import com.hanseongge.domain.board.entity.Popup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PopupRepository extends JpaRepository<Popup, Long> {

    /**
     * 켜져 있는 것 전부, 차례대로.
     *
     * <p>기간 판정은 질의에 넣지 않는다 — {@code Popup#isLiveAt} 하나가 판정하고
     * 서비스가 거른다. 팝업은 많아야 서너 개라 전부 읽어도 비용이 없고, 조건이
     * 두 곳(SQL·코드)에 갈라지면 "왜 안 뜨지"를 두 군데서 찾게 된다.
     */
    List<Popup> findByActiveTrueOrderBySortAscIdAsc();

    List<Popup> findAllByOrderBySortAscIdAsc();
}
