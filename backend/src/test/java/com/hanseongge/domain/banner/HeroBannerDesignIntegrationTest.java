package com.hanseongge.domain.banner;

import com.hanseongge.domain.banner.dto.BannerContent;
import com.hanseongge.domain.banner.dto.HeroBannerReplaceRequest;
import com.hanseongge.domain.banner.entity.BannerKind;
import com.hanseongge.domain.banner.entity.HeroBanner;
import com.hanseongge.domain.banner.repository.HeroBannerRepository;
import com.hanseongge.domain.banner.service.HeroBannerService;
import com.hanseongge.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 꾸민 배너가 <b>DB 를 한 바퀴 돌아</b> 그대로 돌아오는지.
 *
 * <p>내용은 {@code jsonb} 칸에 들어간다. 단위 테스트로는 Hibernate 가 그 칸에 넣고
 * 꺼내는 길이 확인되지 않는다 — 넣는 순간 깨지면 관리자가 저장을 누를 때까지 아무도
 * 모른다.
 *
 * <p>함께 확인하는 것: 저장 경로에서도 CSS 가 걸러지는가. 검증기 단위 테스트가 이미
 * 보지만, 서비스가 검증기를 부르지 않도록 바뀌면 그 테스트는 계속 통과하면서 손님
 * 화면에는 걸러지지 않은 값이 나간다.
 */
@SpringBootTest
class HeroBannerDesignIntegrationTest extends EmbeddedPostgresSupport {

    @Autowired HeroBannerService bannerService;
    @Autowired HeroBannerRepository bannerRepository;

    @AfterEach
    void cleanUp() {
        // 임베디드 PostgreSQL 은 JVM 당 하나이고 테스트 클래스가 같은 DB 를 공유한다.
        // 남겨 두면 다른 클래스의 배너 건수 단언이 어긋난다.
        bannerRepository.deleteAllInBatch();
    }

    private static HeroBannerReplaceRequest.Item design(BannerContent content) {
        return new HeroBannerReplaceRequest.Item(BannerKind.DESIGN, content, null, null, null,
                "기획전 배너");
    }

    @Test
    @DisplayName("꾸민 배너가 jsonb 를 거쳐 그대로 돌아온다")
    void roundTrip() {
        BannerContent content = new BannerContent(
                new BannerContent.Background("#0f1b2d", null, null, 8,
                        BannerContent.Overlay.SOLID),
                BannerContent.Align.CENTER,
                List.of(
                        new BannerContent.Block("b1", BannerContent.BlockType.TEXT,
                                BannerContent.TextStyle.TITLE, "순금 골드바", "#c6a15b", null, null),
                        new BannerContent.Block("b2", BannerContent.BlockType.BUTTON,
                                null, "보러가기", null, "/shop?categoryId=1",
                                BannerContent.ButtonLook.OUTLINE)),
                new BannerContent.Mobile(BannerContent.Align.LEFT, List.of("b1")));

        bannerService.replace(new HeroBannerReplaceRequest(List.of(design(content))), "tester");

        HeroBanner saved = bannerRepository.findAllByOrderBySortAscIdAsc().get(0);
        assertThat(saved.getKind()).isEqualTo(BannerKind.DESIGN);
        assertThat(saved.getImageKey()).isNull();

        BannerContent read = bannerService.contentOf(saved);
        assertThat(read.align()).isEqualTo(BannerContent.Align.CENTER);
        assertThat(read.background().blur()).isEqualTo(8);
        assertThat(read.background().overlay()).isEqualTo(BannerContent.Overlay.SOLID);
        assertThat(read.blocks()).hasSize(2);
        assertThat(read.blocks().get(0).color()).isEqualTo("#c6a15b");
        assertThat(read.blocks().get(1).href()).isEqualTo("/shop?categoryId=1");
        assertThat(read.blocks().get(1).look()).isEqualTo(BannerContent.ButtonLook.OUTLINE);
        assertThat(read.mobile().hide()).containsExactly("b1");
    }

    @Test
    @DisplayName("저장 경로에서도 색인 척한 CSS 는 걸러진다")
    void stripsCssOnSave() {
        BannerContent content = new BannerContent(
                new BannerContent.Background("red; background: url(https://evil.example/x)",
                        null, null, 9999, null),
                null,
                List.of(new BannerContent.Block(null, BannerContent.BlockType.TEXT,
                        BannerContent.TextStyle.TITLE, "제목",
                        "url(https://evil.example/y)", null, null)),
                null);

        bannerService.replace(new HeroBannerReplaceRequest(List.of(design(content))), "tester");

        BannerContent read = bannerService.contentOf(
                bannerRepository.findAllByOrderBySortAscIdAsc().get(0));

        assertThat(read.background().color()).isNull();
        assertThat(read.background().blur()).isZero();
        assertThat(read.blocks().get(0).color()).isNull();
    }

    @Test
    @DisplayName("사진 배너와 한 목록에서 차례를 지킨다")
    void keepsOrderAcrossKinds() {
        BannerContent content = new BannerContent(
                new BannerContent.Background("#0f1b2d", null, null, 0, null), null,
                List.of(new BannerContent.Block("b1", BannerContent.BlockType.TEXT,
                        BannerContent.TextStyle.TITLE, "꾸민 배너", null, null, null)),
                null);

        HeroBannerReplaceRequest.Item image = new HeroBannerReplaceRequest.Item(
                BannerKind.IMAGE, null, "banners/2026/11/a.png", null, "/shop", "사진 배너");

        bannerService.replace(new HeroBannerReplaceRequest(List.of(design(content), image)),
                "tester");

        List<HeroBanner> saved = bannerRepository.findAllByOrderBySortAscIdAsc();
        assertThat(saved).extracting(HeroBanner::getKind)
                .containsExactly(BannerKind.DESIGN, BannerKind.IMAGE);
        assertThat(saved).extracting(HeroBanner::getSort).containsExactly(0, 1);
    }
}
