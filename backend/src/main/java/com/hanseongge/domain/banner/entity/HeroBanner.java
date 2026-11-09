package com.hanseongge.domain.banner.entity;

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
 * 메인 배너 한 장 (V43).
 *
 * <p>문구·버튼까지 이미지 안에 들어간다. 글자를 따로 담지 않는 이유는 그래야 디자인이
 * 이미지 하나로 끝나기 때문이다 — 글자를 밖에 두면 사진의 어느 자리에 글이 올라갈지를
 * 화면 코드가 정하게 되고, 사진이 바뀔 때마다 그 자리가 어긋난다.
 *
 * <p>대신 {@code altText} 는 받는다. 눈으로 보지 않는 사람에게는 이미지 안의 글자가
 * 존재하지 않는 것과 같다.
 */
@Entity
@Table(name = "hero_banners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HeroBanner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_key", length = 300, nullable = false)
    private String imageKey;

    /** 누르면 갈 곳. 비우면 누를 수 없는 배너다. */
    @Column(name = "link_href", length = 500)
    private String linkHref;

    @Column(name = "alt_text", length = 200)
    private String altText;

    @Column(nullable = false)
    private int sort;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by", length = 60)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_by", length = 60)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    private HeroBanner(String imageKey, String linkHref, String altText, int sort, String by) {
        this.imageKey = imageKey;
        this.linkHref = blankToNull(linkHref);
        this.altText = blankToNull(altText);
        this.sort = sort;
        this.active = true;
        this.createdBy = by;
        this.updatedBy = by;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    public static HeroBanner of(String imageKey, String linkHref, String altText, int sort, String by) {
        return new HeroBanner(imageKey, linkHref, altText, sort, by);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
