package com.hanseongge.domain.board.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 공지사항 ({@code notices}).
 *
 * <p>관리자만 쓰고 누구나 읽는다. 회원과의 연결이 없어 {@code created_by} 는 FK 가 아니라
 * 관리자 로그인 아이디 문자열이다 — 상품({@code Product})과 같은 방식이다.
 *
 * <h2>지우지 않고 숨긴다</h2>
 * {@code active=false} 로 내린다. 공지는 "그때 그렇게 안내했다"는 근거로 남는 문서라,
 * 행을 지우면 무엇을 안내했는지 확인할 방법이 사라진다.
 */
@Entity
@Getter
@Table(name = "notices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "content", nullable = false)
    private String content;

    /** 목록 맨 위에 고정. 정렬이 {@code pinned DESC, id DESC} 인 근거다. */
    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_by", length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private Notice(String title, String content, boolean pinned, String createdBy) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.viewCount = 0;
        this.active = true;
        this.createdBy = createdBy;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public static Notice create(String title, String content, boolean pinned, String adminLoginId) {
        return new Notice(title, content, pinned, adminLoginId);
    }

    public void update(String title, String content, boolean pinned, boolean active) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 조회수 증가.
     *
     * <p>{@code updatedAt} 을 건드리지 않는다. 조회는 글의 수정이 아니다 — 여기서 함께
     * 올리면 목록의 "수정일"이 누가 열어볼 때마다 바뀐다.
     */
    public void increaseViewCount() {
        this.viewCount++;
    }
}
