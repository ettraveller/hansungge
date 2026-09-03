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
 * 이벤트 응모글 ({@code event_entries}).
 *
 * <h2>제목이 없다</h2>
 * 레퍼런스의 응모글은 전부 "기프티콘 신청합니다" 류라 제목과 본문이 같은 말이 된다.
 * 한 줄만 받는다.
 *
 * <h2>기본이 비밀글이다</h2>
 * 응모글에는 연락처가 섞이기 쉽다. 실수로 공개되는 쪽이 아니라 <b>일부러 공개하는 쪽</b>이
 * 되도록 기본값을 뒤집어 두었다.
 */
@Entity
@Getter
@Table(name = "event_entries")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "secret", nullable = false)
    private boolean secret;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    private EventEntry(Long eventId, Long userId, String content, boolean secret) {
        this.eventId = eventId;
        this.userId = userId;
        this.content = content;
        this.secret = secret;
        this.active = true;
        this.createdAt = OffsetDateTime.now();
    }

    public static EventEntry create(Long eventId, Long userId, String content, boolean secret) {
        return new EventEntry(eventId, userId, content, secret);
    }

    public void withdraw() {
        this.active = false;
    }

    public boolean isOwnedBy(Long candidateUserId) {
        return candidateUserId != null && candidateUserId.equals(userId);
    }

    /** {@link Qna#isReadableBy} 와 같은 규칙이다. */
    public boolean isReadableBy(Long candidateUserId, boolean admin) {
        return !secret || admin || isOwnedBy(candidateUserId);
    }
}
