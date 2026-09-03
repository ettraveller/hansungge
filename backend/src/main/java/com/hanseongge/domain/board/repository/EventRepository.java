package com.hanseongge.domain.board.repository;

import com.hanseongge.domain.board.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * 공개 목록 — 최근 시작한 이벤트가 위다.
     *
     * <p>종료된 이벤트도 <b>목록에는 남긴다.</b> 지난 이벤트가 통째로 사라지면 "당첨자
     * 발표는 어디서 보나"라는 문의가 곧바로 들어온다. 상태 배지로 구분한다.
     */
    @Query("""
            SELECT e FROM Event e
            WHERE e.active = true
            ORDER BY e.startAt DESC, e.id DESC
            """)
    Page<Event> findPublicPage(Pageable pageable);

    @Query("SELECT e FROM Event e ORDER BY e.startAt DESC, e.id DESC")
    Page<Event> findAdminPage(Pageable pageable);

    Optional<Event> findByIdAndActiveTrue(Long id);
}
