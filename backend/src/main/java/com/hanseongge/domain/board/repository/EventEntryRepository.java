package com.hanseongge.domain.board.repository;

import com.hanseongge.domain.board.entity.EventEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventEntryRepository extends JpaRepository<EventEntry, Long> {

    @Query("""
            SELECT e FROM EventEntry e
            WHERE e.eventId = :eventId AND e.active = true
            ORDER BY e.id DESC
            """)
    Page<EventEntry> findPublicPage(@Param("eventId") Long eventId, Pageable pageable);

    Optional<EventEntry> findByIdAndActiveTrue(Long id);

    /** 한 사람이 같은 이벤트에 두 번 응모하는 것을 막는 근거. */
    boolean existsByEventIdAndUserIdAndActiveTrue(Long eventId, Long userId);

    long countByEventIdAndActiveTrue(Long eventId);

    /**
     * 여러 이벤트의 응모 수를 한 번에.
     *
     * <p>목록에서 이벤트마다 세면 15줄짜리 페이지가 16번의 질의가 된다.
     */
    @Query("""
            SELECT e.eventId, count(e) FROM EventEntry e
            WHERE e.eventId IN :eventIds AND e.active = true
            GROUP BY e.eventId
            """)
    List<Object[]> countByEventIds(@Param("eventIds") List<Long> eventIds);
}
