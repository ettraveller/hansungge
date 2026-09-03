package com.hanseongge.domain.board.repository;

import com.hanseongge.domain.board.entity.Qna;
import com.hanseongge.domain.board.entity.QnaBoardType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QnaRepository extends JpaRepository<Qna, Long> {

    /**
     * 게시판별 공개 목록.
     *
     * <p>비밀글도 <b>목록에는 나온다</b> — 제목과 자물쇠만 보이고 본문은 상세에서 막힌다.
     * 레퍼런스와 같은 방식이고, 그래야 "몇 명이 무엇을 물었는가"가 보여 중복 문의가 준다.
     *
     * <p>정렬을 질의에 박아 둔다. 클라이언트가 정렬을 정하면 인덱스({@code idx_qna_board})를
     * 벗어난 정렬로 전건 스캔이 난다.
     */
    @Query("""
            SELECT q FROM Qna q
            WHERE q.boardType = :boardType AND q.active = true
            ORDER BY q.id DESC
            """)
    Page<Qna> findPublicPage(@Param("boardType") QnaBoardType boardType, Pageable pageable);

    /** 내가 쓴 문의 — 게시판 구분 없이 전부. 마이페이지와 게시판 상단이 함께 쓴다. */
    @Query("""
            SELECT q FROM Qna q
            WHERE q.userId = :userId AND q.active = true
            ORDER BY q.id DESC
            """)
    Page<Qna> findMinePage(@Param("userId") Long userId, Pageable pageable);

    /** 관리자 목록 — 내린 글도 보이고, 미답변만 걸러 볼 수 있다. */
    @Query("""
            SELECT q FROM Qna q
            WHERE (:boardType IS NULL OR q.boardType = :boardType)
              AND (:unansweredOnly = false OR q.answeredAt IS NULL)
            ORDER BY q.id DESC
            """)
    Page<Qna> findAdminPage(@Param("boardType") QnaBoardType boardType,
                            @Param("unansweredOnly") boolean unansweredOnly,
                            Pageable pageable);

    Optional<Qna> findByIdAndActiveTrue(Long id);

    /** 관리자 대시보드용 미답변 건수. */
    long countByAnsweredAtIsNullAndActiveTrue();
}
