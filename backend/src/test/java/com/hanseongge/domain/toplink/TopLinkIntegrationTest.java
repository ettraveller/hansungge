package com.hanseongge.domain.toplink;

import com.hanseongge.domain.toplink.dto.TopLinkReplaceRequest;
import com.hanseongge.domain.toplink.dto.TopLinkReplaceRequest.Item;
import com.hanseongge.domain.toplink.entity.TopLink;
import com.hanseongge.domain.toplink.repository.TopLinkRepository;
import com.hanseongge.domain.toplink.service.TopLinkService;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 상단 띠 링크가 DB 를 한 바퀴 돌아 차례대로 돌아오는지, 그리고 <b>받으면 안 되는
 * 주소를 실제 저장 경로에서 막는지</b>.
 *
 * <p>여기 저장한 값은 모든 화면 맨 위에 {@code href}·{@code src} 로 나간다. 검사를
 * 서비스가 부르지 않도록 바뀌면 손님 화면에서야 드러나므로, 저장 경로째로 본다.
 */
@SpringBootTest
class TopLinkIntegrationTest extends EmbeddedPostgresSupport {

    private static final String ICON = "icons/2026/11/3f2a1b4c-5d6e-4f70-8a91-b2c3d4e5f607.png";

    @Autowired TopLinkService linkService;
    @Autowired TopLinkRepository linkRepository;

    @AfterEach
    void cleanUp() {
        // 임베디드 PostgreSQL 은 테스트 클래스가 함께 쓴다. 남기면 다른 클래스가 본다.
        linkRepository.deleteAllInBatch();
    }

    private void replace(Item... items) {
        linkService.replace(new TopLinkReplaceRequest(List.of(items)), "tester");
    }

    @Test
    @DisplayName("보낸 차례가 띠의 차례다 — 아이콘 링크와 글자 링크가 한 목록에 산다")
    void keepsOrder() {
        replace(new Item("  네이버 블로그 ", " https://blog.naver.com/hanseong ", ICON),
                new Item("농협몰", "https://www.nonghyupmall.com/", "  "),
                new Item("매장안내", "/store", null));

        List<TopLink> saved = linkService.findAll();

        assertThat(saved).extracting(TopLink::getLabel)
                .containsExactly("네이버 블로그", "농협몰", "매장안내");
        assertThat(saved.get(0).getHref()).isEqualTo("https://blog.naver.com/hanseong");
        assertThat(saved.get(0).getIconKey()).isEqualTo(ICON);
        // 공백만 온 아이콘은 "없음"이다. 빈 문자열로 남으면 화면이 깨진 그림을 그린다.
        assertThat(saved.get(1).getIconKey()).isNull();
        assertThat(saved.get(2).getHref()).isEqualTo("/store");
    }

    @Test
    @DisplayName("다시 저장하면 보낸 것이 곧 전체다 — 빈 목록이면 모두 내린다")
    void replacesWhole() {
        replace(new Item("블로그", "https://blog.naver.com/a", null),
                new Item("유튜브", "https://youtube.com/@a", null));

        replace(new Item("유튜브", "https://youtube.com/@a", null));
        assertThat(linkService.findAll()).extracting(TopLink::getLabel).containsExactly("유튜브");

        replace();
        assertThat(linkService.findAll()).isEmpty();
    }

    @ParameterizedTest(name = "받지 않는 주소: {0}")
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JaVaScRiPt:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "//evil.example/path",
            "/\\evil.example",
            "smartstore.naver.com/hanseong",
            "https://",
            "https://a b.example",
            "ftp://files.example/a",
    })
    void rejectsUnsafeHref(String href) {
        assertThatThrownBy(() -> replace(new Item("링크", href, null)))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest(name = "받지 않는 아이콘: {0}")
    @ValueSource(strings = {
            // mediaUrl 은 https:// 로 시작하는 키를 주소 그대로 쓴다 — 남의 서버 그림이 된다
            "https://evil.example/pixel.png",
            "//evil.example/pixel.png",
            "icons/../../etc/passwd",
            "icons/2026/11/not-a-uuid.png",
            "icons/2026/11/3f2a1b4c-5d6e-4f70-8a91-b2c3d4e5f607.svg",
    })
    void rejectsForeignIcon(String iconKey) {
        assertThatThrownBy(() -> replace(new Item("링크", "https://example.com", iconKey)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("이름이 없으면 받지 않는다 — 아이콘이 있어도")
    void requiresLabel() {
        assertThatThrownBy(() -> replace(new Item("  ", "https://example.com", ICON)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이름");
    }

    @Test
    @DisplayName("틀린 링크가 하나 섞이면 걸려 있던 링크를 건드리지 않는다")
    void keepsExistingWhenRejected() {
        replace(new Item("블로그", "https://blog.naver.com/a", null));

        assertThatThrownBy(() -> replace(
                new Item("유튜브", "https://youtube.com/@a", null),
                new Item("나쁜 링크", "javascript:alert(1)", null)))
                .isInstanceOf(BusinessException.class);

        assertThat(linkService.findAll()).extracting(TopLink::getLabel).containsExactly("블로그");
    }
}
