package com.hanseongge.domain.board.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 메인 팝업 ({@code popups}).
 *
 * <p>첫 화면에 띄우는 알림창. 지금 쓰는 것은 매입 계산기지만, 다음에 올 것이
 * 이벤트·휴무 안내·신상품이라 관리자가 직접 넣고 끄는 틀로 둔다.
 *
 * <h2>노출 기간을 반드시 생각하게 한다</h2>
 * 끝나는 날 없는 팝업은 반드시 잊혀진다 — 지난 설 인사가 여름까지 떠 있는 쇼핑몰이
 * 그래서 생긴다. 비워 둘 수는 있지만 그 뜻은 "사람이 꺼 줄 때까지 계속"이고,
 * 관리자 화면이 그렇게 적어 준다.
 */
@Entity
@Getter
@Table(name = "popups")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Popup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 관리자 목록에서 구분하는 이름. 고객에게는 보이지 않는다 */
    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PopupKind kind;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(name = "image_key", length = 300)
    private String imageKey;

    @Column(name = "link_href", length = 500)
    private String linkHref;

    @Column(name = "link_label", length = 60)
    private String linkLabel;

    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private int sort;

    /**
     * 「오늘 하루 보지 않기」를 띄울지.
     *
     * <p>끌 수 있어야 한다 — 휴무 안내처럼 반드시 봐야 하는 것은 하루 숨기면 그날
     * 방문한 사람이 끝내 못 본다. 기본은 켬이다. 매일 같은 팝업이 뜨는 것만큼
     * 빨리 미움받는 것이 없다.
     */
    @Column(nullable = false)
    private boolean dismissible;

    @Column(name = "created_by", length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", length = 60)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Popup create(String name, PopupKind kind, String title, String body,
                               String imageKey, String linkHref, String linkLabel,
                               OffsetDateTime startsAt, OffsetDateTime endsAt,
                               boolean active, int sort, boolean dismissible, String actor) {
        Popup popup = new Popup();
        popup.apply(name, kind, title, body, imageKey, linkHref, linkLabel,
                startsAt, endsAt, active, sort, dismissible);
        popup.createdBy = actor;
        popup.createdAt = OffsetDateTime.now();
        popup.updatedBy = actor;
        popup.updatedAt = popup.createdAt;
        return popup;
    }

    public void update(String name, PopupKind kind, String title, String body,
                       String imageKey, String linkHref, String linkLabel,
                       OffsetDateTime startsAt, OffsetDateTime endsAt,
                       boolean active, int sort, boolean dismissible, String actor) {
        apply(name, kind, title, body, imageKey, linkHref, linkLabel,
                startsAt, endsAt, active, sort, dismissible);
        this.updatedBy = actor;
        this.updatedAt = OffsetDateTime.now();
    }

    /** 끄고 켜기만. 기간을 지우지 않고 잠시 내릴 때 쓴다 */
    public void changeActive(boolean active, String actor) {
        this.active = active;
        this.updatedBy = actor;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 지금 띄울 팝업인가.
     *
     * <p>기간 판정을 <b>DB 질의가 아니라 여기서</b> 한다. 질의에 넣으면 "왜 안 뜨지"를
     * 디버깅할 때 SQL 을 읽어야 하고, 캐시를 얹는 날 조건이 둘로 갈라진다.
     */
    public boolean isLiveAt(OffsetDateTime now) {
        if (!active) return false;
        if (startsAt != null && now.isBefore(startsAt)) return false;
        return endsAt == null || now.isBefore(endsAt);
    }

    private void apply(String name, PopupKind kind, String title, String body,
                       String imageKey, String linkHref, String linkLabel,
                       OffsetDateTime startsAt, OffsetDateTime endsAt,
                       boolean active, int sort, boolean dismissible) {
        this.name = name;
        this.kind = kind;
        // 계산기는 내용이 코드에 있다. 관리자가 적어 둔 제목·본문이 남아 있으면
        // 종류를 바꿨을 때 안 쓰는 글이 DB 에 떠돌고, 되돌렸을 때 옛 글이 되살아난다.
        this.title = kind == PopupKind.CALCULATOR ? null : title;
        this.body = kind == PopupKind.CALCULATOR ? null : body;
        this.imageKey = kind == PopupKind.CALCULATOR ? null : imageKey;
        this.linkHref = linkHref;
        this.linkLabel = linkHref == null ? null : linkLabel;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.active = active;
        this.sort = sort;
        this.dismissible = dismissible;
    }
}
