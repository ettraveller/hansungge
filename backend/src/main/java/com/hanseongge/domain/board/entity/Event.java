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
 * 이벤트 ({@code events}).
 *
 * <p>관리자가 열고, 그 아래에 고객이 {@link EventEntry} 로 응모한다. 레퍼런스는 이벤트
 * 게시판을 <b>응모글만</b> 쌓이는 곳으로 쓰지만("기프티콘 신청합니다"가 98건), 그러면
 * 무슨 이벤트인지가 어디에도 없다 — 응모하는 사람은 알고 왔겠지만 처음 온 사람은 모른다.
 * 그래서 이벤트 자체를 먼저 두고 응모를 그 아래에 붙인다.
 */
@Entity
@Getter
@Table(name = "events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "content")
    private String content;

    @Column(name = "thumbnail_key", length = 300)
    private String thumbnailKey;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private Event(String title, String content, String thumbnailKey,
                  OffsetDateTime startAt, OffsetDateTime endAt) {
        this.title = title;
        this.content = content;
        this.thumbnailKey = thumbnailKey;
        this.startAt = startAt;
        this.endAt = endAt;
        this.viewCount = 0;
        this.active = true;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public static Event create(String title, String content, String thumbnailKey,
                               OffsetDateTime startAt, OffsetDateTime endAt) {
        return new Event(title, content, thumbnailKey, startAt, endAt);
    }

    public void update(String title, String content, String thumbnailKey,
                       OffsetDateTime startAt, OffsetDateTime endAt, boolean active) {
        this.title = title;
        this.content = content;
        this.thumbnailKey = thumbnailKey;
        this.startAt = startAt;
        this.endAt = endAt;
        this.active = active;
        this.updatedAt = OffsetDateTime.now();
    }

    public void hide() {
        this.active = false;
        this.updatedAt = OffsetDateTime.now();
    }

    public void increaseViewCount() {
        this.viewCount++;
    }

    /**
     * 진행 상태 — <b>저장하지 않고 계산한다.</b>
     *
     * <p>컬럼으로 두면 기간이 지나도 누군가 값을 바꿔 주기 전까지 "진행중"으로 남는다.
     * 그 일을 스케줄러에 맡기면 스케줄러가 멎은 날 종료된 이벤트에 응모가 들어온다.
     * 기간과 현재 시각만 있으면 답이 나오는 것을 굳이 저장할 이유가 없다.
     */
    public EventStatus status() {
        OffsetDateTime now = OffsetDateTime.now();
        if (now.isBefore(startAt)) return EventStatus.UPCOMING;
        if (now.isAfter(endAt)) return EventStatus.CLOSED;
        return EventStatus.OPEN;
    }

    public boolean isOpen() {
        return status() == EventStatus.OPEN;
    }
}
