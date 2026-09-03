package com.hanseongge.domain.board.repository;

import com.hanseongge.domain.board.entity.Notice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NoticeRepository extends JpaRepository<Notice, Long> {

    /**
     * 공개 목록 — 고정 공지를 위로 올린다.
     *
     * <p>정렬을 질의에 박아 둔다. {@code Pageable} 의 정렬을 그대로 받으면 클라이언트가
     * 아무 컬럼으로나 정렬을 요구할 수 있고, 인덱스를 벗어난 정렬은 전건 스캔이 된다 —
     * 상품 목록({@code ProductRepositoryImpl})이 같은 이유로 정렬을 고정한다.
     */
    @Query("""
            SELECT n FROM Notice n
            WHERE n.active = true
            ORDER BY n.pinned DESC, n.id DESC
            """)
    Page<Notice> findPublicPage(Pageable pageable);

    /** 관리자 목록 — 내린 공지도 보인다. */
    @Query("SELECT n FROM Notice n ORDER BY n.pinned DESC, n.id DESC")
    Page<Notice> findAdminPage(Pageable pageable);

    Optional<Notice> findByIdAndActiveTrue(Long id);

    /** 메인 화면 요약 위젯용 상위 N 건. 개수는 호출부가 {@code Pageable} 로 정한다. */
    @Query("""
            SELECT n FROM Notice n
            WHERE n.active = true
            ORDER BY n.pinned DESC, n.id DESC
            """)
    List<Notice> findPublicTop(Pageable pageable);
}
