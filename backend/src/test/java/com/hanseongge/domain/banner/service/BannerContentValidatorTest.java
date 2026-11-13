package com.hanseongge.domain.banner.service;

import com.hanseongge.domain.banner.dto.BannerContent;
import com.hanseongge.global.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 꾸민 배너의 내용이 손님 화면에 들어가기 전에 무엇을 걸러내는지.
 *
 * <p>이 값은 손님 화면의 {@code style} 로 들어간다. 색을 글자 그대로 받으면
 * {@code url(https://남의서버/…)} 한 줄로 우리 화면을 연 사람의 접속이 밖으로 나간다.
 * 관리자 화면에서 고르게 만들어 두어도 요청은 화면을 거치지 않고 올 수 있으므로,
 * 여기가 마지막 문이다.
 */
class BannerContentValidatorTest {

    private final BannerContentValidator validator = new BannerContentValidator();

    private static BannerContent.Block text(String value) {
        return new BannerContent.Block(null, BannerContent.BlockType.TEXT,
                BannerContent.TextStyle.TITLE, value, null, null, null);
    }

    private static BannerContent of(BannerContent.Background background,
                                    BannerContent.Block... blocks) {
        return new BannerContent(background, null, List.of(blocks), null);
    }

    @Nested
    @DisplayName("색")
    class Colors {

        @Test
        @DisplayName("#rrggbb 만 통과한다")
        void onlyHex() {
            BannerContent.Block block = new BannerContent.Block(null, BannerContent.BlockType.TEXT,
                    BannerContent.TextStyle.TITLE, "제목", "#C6A15B", null, null);

            assertThat(validator.normalize(of(null, block)).blocks().get(0).color())
                    .isEqualTo("#c6a15b");
        }

        @Test
        @DisplayName("CSS 를 색인 척 끼워 넣으면 그 칸만 비운다")
        void dropsCssInjection() {
            String[] attacks = {
                    "red; background: url(https://evil.example/collect)",
                    "url(https://evil.example/x.png)",
                    "var(--anything)",
                    "rgb(1,2,3)",
                    "#12345",
            };

            for (String attack : attacks) {
                BannerContent.Block block = new BannerContent.Block(null,
                        BannerContent.BlockType.TEXT, BannerContent.TextStyle.TITLE,
                        "제목", attack, null, null);

                assertThat(validator.normalize(of(null, block)).blocks().get(0).color())
                        .as("색으로 받아들이면 안 된다: %s", attack)
                        .isNull();
            }
        }
    }

    @Nested
    @DisplayName("배경")
    class Background {

        @Test
        @DisplayName("흐림은 정해진 단계만. 자유 숫자를 받으면 화면을 지울 수 있다")
        void blurSteps() {
            assertThat(background(16).blur()).isEqualTo(16);
            assertThat(background(9999).blur()).isZero();
            assertThat(background(-4).blur()).isZero();
        }

        @Test
        @DisplayName("바깥 주소를 사진 키로 넣지 못한다")
        void rejectsExternalImage() {
            BannerContent.Background raw = new BannerContent.Background(
                    null, "https://evil.example/x.png", "../../etc/passwd", 0, null);

            BannerContent.Background clean = validator
                    .normalize(of(raw, text("제목"))).background();

            assertThat(clean.imageKey()).isNull();
            assertThat(clean.mobileImageKey()).isNull();
        }

        private BannerContent.Background background(int blur) {
            return validator
                    .normalize(of(new BannerContent.Background("#0f1b2d", null, null, blur, null),
                            text("제목")))
                    .background();
        }
    }

    @Nested
    @DisplayName("버튼")
    class Buttons {

        @Test
        @DisplayName("우리 화면 안의 주소만 받는다")
        void internalOnly() {
            assertThat(button("/shop?categoryId=1").href()).isEqualTo("/shop?categoryId=1");

            // // 는 프로토콜을 생략한 바깥 주소다. / 로 시작하는지만 보면 통과한다.
            for (String outside : new String[] {"https://evil.example", "//evil.example", "shop"}) {
                assertThatThrownBy(() -> button(outside))
                        .as("바깥 주소: %s", outside)
                        .isInstanceOf(BusinessException.class);
            }
        }

        @Test
        @DisplayName("갈 곳이 없으면 거절한다 — 조용히 지우면 버튼이 사라진 줄 모른다")
        void needsHref() {
            assertThatThrownBy(() -> button(null)).isInstanceOf(BusinessException.class);
        }

        private BannerContent.Block button(String href) {
            BannerContent.Block raw = new BannerContent.Block(null,
                    BannerContent.BlockType.BUTTON, null, "보러가기", null, href, null);
            return validator.normalize(of(null, raw)).blocks().get(0);
        }
    }

    @Nested
    @DisplayName("블록")
    class Blocks {

        @Test
        @DisplayName("겹치거나 빈 id 는 자리 번호로 다시 매긴다 — 엉뚱한 블록이 숨겨진다")
        void rewritesIds() {
            BannerContent.Block a = new BannerContent.Block("same", BannerContent.BlockType.TEXT,
                    BannerContent.TextStyle.TITLE, "하나", null, null, null);
            BannerContent.Block b = new BannerContent.Block("same", BannerContent.BlockType.TEXT,
                    BannerContent.TextStyle.DESC, "둘", null, null, null);

            List<BannerContent.Block> blocks = validator.normalize(of(null, a, b)).blocks();

            assertThat(blocks).extracting(BannerContent.Block::id).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("없는 블록을 숨기라는 지시는 걷어낸다")
        void dropsDanglingHide() {
            BannerContent raw = new BannerContent(null, null, List.of(text("제목")),
                    new BannerContent.Mobile(null, List.of("b1", "사라진것")));

            assertThat(validator.normalize(raw).mobile().hide()).containsExactly("b1");
        }

        @Test
        @DisplayName("글자 없는 글자 블록은 거절한다")
        void needsText() {
            assertThatThrownBy(() -> validator.normalize(of(null, text("   "))))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("배경도 내용도 없으면 검은 상자만 남는다 — 거절한다")
        void needsSomething() {
            assertThatThrownBy(() -> validator.normalize(new BannerContent(null, null, List.of(), null)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    /**
     * 크기와 자리.
     *
     * <p>둘 다 숫자 그대로 {@code style} 로 나간다. 크기는 끝을 당기고, 자리는
     * <b>x·y 가 함께 있을 때만</b> 인정한다 — 한쪽만 걸면 다른 쪽은 흐름이 정하는
     * 자리에 남아, 끌어다 둔 적 없는 곳에 글자가 선다.
     */
    @Nested
    @DisplayName("크기와 자리")
    class SizeAndPlace {

        private BannerContent.Block block(Integer size, Integer x, Integer y) {
            return new BannerContent.Block("b1", BannerContent.BlockType.TEXT,
                    BannerContent.TextStyle.TITLE, "제목", null, null, null, size, true, x, y,
                    null, null);
        }

        private BannerContent.Block normalized(BannerContent.Block raw) {
            return validator.normalize(of(null, raw)).blocks().get(0);
        }

        @Test
        @DisplayName("크기는 10~120 으로 당긴다")
        void clampsSize() {
            assertThat(normalized(block(9999, null, null)).size()).isEqualTo(120);
            assertThat(normalized(block(0, null, null)).size()).isEqualTo(10);
            assertThat(normalized(block(48, null, null)).size()).isEqualTo(48);
            assertThat(normalized(block(null, null, null)).size()).isNull();
        }

        @Test
        @DisplayName("굵기는 고른 그대로 간다")
        void keepsBold() {
            assertThat(normalized(block(null, null, null)).bold()).isTrue();
        }

        @Test
        @DisplayName("자리는 0~100 으로 당긴다")
        void clampsPercent() {
            BannerContent.Block out = normalized(block(null, -20, 400));
            assertThat(out.x()).isZero();
            assertThat(out.y()).isEqualTo(100);
        }

        @Test
        @DisplayName("한쪽만 온 자리는 둘 다 버린다")
        void dropsHalfPlacement() {
            assertThat(normalized(block(null, 50, null)).x()).isNull();
            assertThat(normalized(block(null, null, 50)).y()).isNull();
        }

        @Test
        @DisplayName("버튼도 크기·자리를 들고 간다")
        void appliesToButtons() {
            BannerContent.Block raw = new BannerContent.Block("b1",
                    BannerContent.BlockType.BUTTON, null, "보러가기", null, "/shop",
                    BannerContent.ButtonLook.GOLD, 20, false, 70, 80, null, null);

            BannerContent.Block out = validator.normalize(of(null, raw)).blocks().get(0);
            assertThat(out.size()).isEqualTo(20);
            assertThat(out.bold()).isFalse();
            assertThat(out.x()).isEqualTo(70);
            assertThat(out.y()).isEqualTo(80);
        }
    }

    /**
     * 배너 <b>안에</b> 얹는 사진.
     *
     * <p>키가 없으면 손님 화면에 깨진 그림 자리만 남고, 그 자리는 글자로 메울 수도
     * 없다 — 그래서 조용히 지우지 않고 거절한다. 대체 텍스트도 같은 이유로 요구한다.
     */
    @Nested
    @DisplayName("사진 블록")
    class ImageBlock {

        private BannerContent.Block image(String key, String alt, Integer width) {
            return image(key, alt, width, null);
        }

        private BannerContent.Block image(String key, String alt, Integer width,
                                          BannerContent.ImageEffect effect) {
            return new BannerContent.Block("b1", BannerContent.BlockType.IMAGE, null, alt,
                    null, null, null, width, null, null, null, key, effect);
        }

        @Test
        @DisplayName("사진 키를 들고 가고, 너비는 20~800 으로 당긴다")
        void keepsImage() {
            BannerContent.Block out = validator
                    .normalize(of(null, image("banners/2026/11/a.webp", "금고 사진", 9999)))
                    .blocks().get(0);

            assertThat(out.imageKey()).isEqualTo("banners/2026/11/a.webp");
            assertThat(out.size()).isEqualTo(800);
            assertThat(out.text()).isEqualTo("금고 사진");
        }

        @Test
        @DisplayName("올린 사진이 없으면 거절한다")
        void requiresKey() {
            assertThatThrownBy(() -> validator.normalize(of(null, image(null, "금고 사진", 240))))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("바깥 주소는 사진 키가 아니다")
        void rejectsExternalKey() {
            assertThatThrownBy(() -> validator
                    .normalize(of(null, image("https://evil.example/a.png", "사진", 240))))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("효과의 모르는 값은 그 칸만 기본으로 되돌린다")
        void clampsEffect() {
            BannerContent.ImageEffect raw = new BannerContent.ImageEffect(
                    BannerContent.FadeEdge.STRONG, 9999, 0, null, null);

            BannerContent.ImageEffect out = validator
                    .normalize(of(null, image("banners/a.webp", "사진", 240, raw)))
                    .blocks().get(0).effect();

            assertThat(out.fade()).isEqualTo(BannerContent.FadeEdge.STRONG);
            assertThat(out.blur()).isZero();
            assertThat(out.opacity()).isEqualTo(100);
            assertThat(out.radius()).isEqualTo(BannerContent.Radius.SMALL);
            assertThat(out.shadow()).isEqualTo(BannerContent.Shadow.NONE);
        }

        @Test
        @DisplayName("글자 블록이 들고 온 사진 효과는 버린다")
        void dropsEffectOnText() {
            BannerContent.Block raw = new BannerContent.Block("b1",
                    BannerContent.BlockType.TEXT, BannerContent.TextStyle.TITLE, "제목",
                    null, null, null, null, null, null, null, null,
                    new BannerContent.ImageEffect(BannerContent.FadeEdge.SOFT, 8, 70, null, null));

            assertThat(validator.normalize(of(null, raw)).blocks().get(0).effect()).isNull();
        }

        @Test
        @DisplayName("대체 텍스트가 없으면 거절한다")
        void requiresAlt() {
            assertThatThrownBy(() -> validator
                    .normalize(of(null, image("banners/a.webp", "   ", 240))))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
