package com.hanseongge.domain.banner.service;

import com.hanseongge.domain.banner.dto.BannerContent;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 꾸민 배너의 내용을 받아들일 수 있는 모양으로 다듬는다.
 *
 * <h2>왜 화면 검사로 끝내지 않는가</h2>
 * 이 값은 손님 화면의 {@code style} 로 들어간다. 색을 글자 그대로 받으면
 * {@code url(https://남의서버/수집?…)} 한 줄로 우리 화면을 연 사람의 접속이 밖으로
 * 나간다. 관리자 화면에서 고르게 만들어 두어도 요청은 화면을 거치지 않고 올 수 있다.
 *
 * <h2>거절보다 다듬기</h2>
 * 색이 이상하면 그 칸만 비우고, 길면 자른다. 블록 열 개를 짜 놓고 저장을 눌렀을 때
 * 색 하나 때문에 전부 되돌아오면 작업이 통째로 날아간다. 다만 <b>이것이 없으면
 * 배너가 성립하지 않는 것</b>(글자 없는 글자 블록, 갈 곳 없는 버튼)은 거절한다 —
 * 조용히 지우면 "저장했는데 버튼이 사라졌다" 가 된다.
 */
@Component
public class BannerContentValidator {

    /** 한 배너에 쌓을 수 있는 요소 수. 이보다 많으면 배너가 아니라 문서다. */
    private static final int MAX_BLOCKS = 8;

    /** 글자 한 블록의 길이. 제목이 길어지면 휴대폰에서 배너를 덮는다. */
    private static final int MAX_TEXT = 200;

    private static final int MAX_BUTTON_TEXT = 40;

    /** 색은 이 모양 하나만 받는다. 이름(red)·함수(rgb())·변수는 받지 않는다. */
    private static final Pattern COLOR = Pattern.compile("^#[0-9a-fA-F]{6}$");

    /** 업로드된 사진 키. {@code :} 와 맨 앞 {@code /} 가 막히면 외부 주소가 닫힌다. */
    private static final Pattern IMAGE_KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9/_.-]*$");

    /** 고를 수 있는 흐림. 자유 숫자를 받으면 blur(9999px) 로 화면을 지울 수 있다. */
    private static final Set<Integer> BLUR_STEPS = Set.of(0, 4, 8, 16);

    public BannerContent normalize(BannerContent raw) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "배너 내용이 비어 있습니다.");
        }

        List<BannerContent.Block> blocks = normalizeBlocks(raw.blocks());
        BannerContent.Background background = normalizeBackground(raw.background());

        if (blocks.isEmpty() && background.imageKey() == null && background.color() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "배너에 배경이나 내용 중 하나는 있어야 합니다.");
        }

        return new BannerContent(
                background,
                raw.align() == null ? BannerContent.Align.LEFT : raw.align(),
                blocks,
                normalizeMobile(raw.mobile(), blocks));
    }

    private BannerContent.Background normalizeBackground(BannerContent.Background raw) {
        if (raw == null) {
            return new BannerContent.Background(null, null, null, 0, BannerContent.Overlay.GRADIENT);
        }
        return new BannerContent.Background(
                color(raw.color()),
                imageKey(raw.imageKey()),
                imageKey(raw.mobileImageKey()),
                BLUR_STEPS.contains(raw.blur()) ? raw.blur() : 0,
                raw.overlay() == null ? BannerContent.Overlay.GRADIENT : raw.overlay());
    }

    private List<BannerContent.Block> normalizeBlocks(List<BannerContent.Block> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > MAX_BLOCKS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "배너에 넣을 수 있는 요소는 " + MAX_BLOCKS + "개까지입니다.");
        }

        List<BannerContent.Block> blocks = new ArrayList<>(raw.size());
        Set<String> seenIds = new HashSet<>();

        for (int i = 0; i < raw.size(); i++) {
            BannerContent.Block block = raw.get(i);
            BannerContent.BlockType type =
                    block.type() == null ? BannerContent.BlockType.TEXT : block.type();

            String text = trim(block.text());
            if (text.isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        (i + 1) + "번째 요소에 글자가 없습니다.");
            }

            // id 는 휴대폰에서 숨길 블록을 가리키는 이름이다. 겹치면 엉뚱한 블록이
            // 사라지므로, 비었거나 겹치면 자리 번호로 다시 매긴다.
            String id = trim(block.id());
            if (id.isEmpty() || !seenIds.add(id)) {
                id = "b" + (i + 1);
                seenIds.add(id);
            }

            if (type == BannerContent.BlockType.BUTTON) {
                String href = internalHref(block.href());
                if (href == null) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "버튼에는 누르면 갈 곳이 있어야 합니다. (" + text + ")");
                }
                blocks.add(new BannerContent.Block(id, type, null,
                        cut(text, MAX_BUTTON_TEXT), color(block.color()), href,
                        block.look() == null ? BannerContent.ButtonLook.GOLD : block.look()));
            } else {
                blocks.add(new BannerContent.Block(id, type,
                        block.style() == null ? BannerContent.TextStyle.DESC : block.style(),
                        cut(text, MAX_TEXT), color(block.color()), null, null));
            }
        }
        return blocks;
    }

    /** 숨길 블록 목록에서 <b>없는 이름</b>을 걷어낸다. 남겨 두면 지운 블록을 영원히 가리킨다. */
    private BannerContent.Mobile normalizeMobile(BannerContent.Mobile raw,
                                                 List<BannerContent.Block> blocks) {
        if (raw == null) {
            return null;
        }
        Set<String> ids = new HashSet<>();
        for (BannerContent.Block block : blocks) {
            ids.add(block.id());
        }
        List<String> hide = raw.hide() == null ? List.of()
                : raw.hide().stream().filter(ids::contains).toList();
        return new BannerContent.Mobile(raw.align(), hide);
    }

    private static String color(String raw) {
        String value = trim(raw);
        return COLOR.matcher(value).matches() ? value.toLowerCase() : null;
    }

    private static String imageKey(String raw) {
        String value = trim(raw);
        if (value.isEmpty() || value.contains("..") || !IMAGE_KEY.matcher(value).matches()) {
            return null;
        }
        return value;
    }

    /**
     * 우리 화면 안의 주소만 받는다.
     *
     * <p>{@code //evil.example} 은 프로토콜을 생략한 바깥 주소라 함께 막는다 —
     * {@code /} 로 시작하는지만 보면 통과한다.
     */
    private static String internalHref(String raw) {
        String value = trim(raw);
        if (!value.startsWith("/") || value.startsWith("//") || value.length() > 500) {
            return null;
        }
        return value;
    }

    private static String trim(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String cut(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
