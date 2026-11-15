package com.hanseongge.domain.banner.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /**
     * 사진 한 장짜리 배너인가, 화면에서 꾸민 배너인가 (V45).
     *
     * <p>DESIGN 은 {@link #content} 에 배경과 쌓을 요소들을 담는다. 글자 한 줄을
     * 고치자고 사진을 다시 받아야 하던 것이 이 갈래가 생긴 이유다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20, nullable = false)
    private BannerKind kind;

    /**
     * DESIGN 배너의 내용 (V45). 모양은 {@code BannerContent}.
     *
     * <p>문자열로 들고 있다 — 이 프로젝트의 다른 JSON 칸(감사로그·결제 응답)과 같은
     * 방식이다. 서버에서 읽을 일이 없고, 넣기 전에 {@code BannerContentValidator} 가
     * 이미 다듬은 값이라 엔티티가 모양을 다시 알 필요가 없다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content")
    private String content;

    @Column(name = "image_key", length = 300)
    private String imageKey;

    /**
     * 휴대폰용 이미지. 비우면 {@link #imageKey} 를 그대로 쓴다.
     *
     * <p>히어로의 비율이 화면 폭에 따라 뒤집힌다 — 데스크톱은 가로로 긴 2.3:1,
     * 휴대폰은 세로로 긴 0.85:1 이다. 한 장으로 둘 다 덮으면 가로가 3분의 2쯤
     * 잘려 나가고, 이미지 안에 넣은 문구가 화면 밖으로 밀린다.
     */
    @Column(name = "mobile_image_key", length = 300)
    private String mobileImageKey;

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

    private HeroBanner(BannerKind kind, String imageKey, String mobileImageKey, String content,
                       String linkHref, String altText, int sort, String by) {
        this.kind = kind;
        this.imageKey = blankToNull(imageKey);
        this.mobileImageKey = blankToNull(mobileImageKey);
        this.content = blankToNull(content);
        this.linkHref = blankToNull(linkHref);
        this.altText = blankToNull(altText);
        this.sort = sort;
        this.active = true;
        this.createdBy = by;
        this.updatedBy = by;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    /** 사진 한 장짜리 배너. */
    public static HeroBanner ofImage(String imageKey, String mobileImageKey, String linkHref,
                                     String altText, int sort, String by) {
        return new HeroBanner(BannerKind.IMAGE, imageKey, mobileImageKey, null,
                linkHref, altText, sort, by);
    }

    /**
     * 화면에서 꾸민 배너.
     *
     * <p>{@code content} 는 {@code BannerContentValidator} 를 통과한 JSON 이어야 한다.
     * 다듬지 않은 값을 그대로 넣으면 손님 화면의 {@code style} 에 그대로 들어간다.
     *
     * <p>누르면 갈 곳을 따로 받지 않는다 — 꾸민 배너는 <b>안에 있는 버튼</b>이 그 일을
     * 한다. 배너 전체를 덮는 링크를 함께 두면 버튼을 피해 누른 손가락이 엉뚱한 곳으로
     * 가고, 버튼이 여러 개일 때는 어느 쪽이 이기는지 알 수 없다.
     */
    public static HeroBanner ofDesign(String content, String altText, int sort, String by) {
        return new HeroBanner(BannerKind.DESIGN, null, null, content, null, altText, sort, by);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
