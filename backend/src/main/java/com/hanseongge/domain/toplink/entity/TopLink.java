package com.hanseongge.domain.toplink.entity;

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
 * 상단 유틸 띠의 링크 하나 (V48).
 *
 * <p>아이콘이 있으면 그림 버튼으로, 없으면 글자 링크로 그린다. 갈래를 따로 적는 칸을
 * 두지 않는 이유는 그 둘이 어긋날 수 있기 때문이다 — "아이콘 링크인데 아이콘이 없다"
 * 는 행이 생기면 화면이 그릴 것이 없다.
 *
 * <p>고치는 길이 없다. 목록을 통째로 갈아 끼우므로({@code TopLinkService#replace})
 * 행은 만들어지고 지워질 뿐이다.
 */
@Entity
@Table(name = "top_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TopLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 글자 링크의 글자이자, 아이콘 링크를 화면 낭독기가 읽는 이름. */
    @Column(nullable = false, length = 40)
    private String label;

    @Column(nullable = false, length = 500)
    private String href;

    /** 올린 아이콘의 미디어 키. null 이면 글자 링크다. */
    @Column(name = "icon_key", length = 300)
    private String iconKey;

    @Column(nullable = false)
    private int sort;

    @Column(name = "created_by", length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * {@code label}·{@code href}·{@code iconKey} 는 {@code TopLinkService} 가 이미
     * 다듬고 검사한 값이어야 한다. 받은 그대로 넣으면 손님 화면의 {@code href} 와
     * {@code src} 로 그대로 나간다.
     */
    public static TopLink of(String label, String href, String iconKey, int sort, String by) {
        TopLink link = new TopLink();
        link.label = label;
        link.href = href;
        link.iconKey = iconKey;
        link.sort = sort;
        link.createdBy = by;
        link.createdAt = OffsetDateTime.now();
        return link;
    }
}
