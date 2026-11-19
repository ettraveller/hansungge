package com.hanseongge.domain.toplink.service;

import com.hanseongge.domain.toplink.dto.TopLinkReplaceRequest;
import com.hanseongge.domain.toplink.entity.TopLink;
import com.hanseongge.domain.toplink.repository.TopLinkRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.audit.AuditLog;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 상단 유틸 띠의 링크.
 *
 * <h2>통째로 갈아 끼운다</h2>
 * 메인 배너({@code HeroBannerService})와 같은 방식이다. 한 트랜잭션에서 전부 지우고
 * 전부 다시 넣는다 — 링크에는 그 id 를 가리키는 다른 데이터가 없어 id 를 이어 줄
 * 의미가 없고, 순서 바꾸기·더하기·빼기가 요청 하나로 끝난다.
 *
 * <h2>받은 주소를 믿지 않는다</h2>
 * 여기 저장한 값은 <b>모든 화면 맨 위</b>에 {@code <a href>} 와 {@code <img src>} 로
 * 나간다. 관리자 계정 하나가 넘어갔을 때 그 자리가 곧 전 고객에게 닿는 통로가 되므로,
 * 화면에서 막았더라도 여기서 다시 본다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TopLinkService {

    static final int MAX_LABEL = 40;
    static final int MAX_HREF = 500;

    /**
     * 받아 주는 주소.
     *
     * <p>{@code https://…}·{@code http://…} 이거나, {@code /shop} 처럼 슬래시 하나로
     * 시작하는 사이트 안 경로다. {@code javascript:} 는 누르는 순간 우리 화면에서
     * 스크립트가 돌고, {@code //남의서버} 는 경로처럼 보이지만 남의 서버로 간다 —
     * 둘째 글자가 슬래시·역슬래시인 것을 따로 막는 이유다.
     */
    private static final Pattern EXTERNAL = Pattern.compile("^https?://[^\\s/]+\\S*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTERNAL = Pattern.compile("^/(?![/\\\\])\\S*$");

    /**
     * 아이콘으로 받아 주는 키 — <b>우리가 저장하며 만든 모양</b>뿐이다
     * ({@code 폴더/yyyy/MM/uuid.확장자}, {@code LocalFileMediaStorage}).
     *
     * <p>화면의 {@code mediaUrl} 은 {@code https://} 로 시작하는 값을 주소 그대로 쓴다.
     * 여기서 모양을 보지 않으면 아이콘 자리에 남의 서버 주소를 넣을 수 있고, 그러면
     * 사이트를 여는 모든 손님의 접속이 그 서버에 찍힌다.
     */
    private static final Pattern ICON_KEY = Pattern.compile(
            "^[a-z]+/\\d{4}/\\d{2}/[0-9a-f-]{36}\\.(jpg|png|gif|webp)$");

    private final TopLinkRepository linkRepository;

    /** 띠에 나가는 차례대로. 손님 화면과 관리자 화면이 같은 것을 읽는다. */
    @Transactional(readOnly = true)
    public List<TopLink> findAll() {
        return linkRepository.findAllByOrderBySortAscIdAsc();
    }

    @Transactional
    @AuditLog(action = AuditActions.TOP_LINK_REPLACE,
              targetType = AuditActions.TARGET_TOP_LINK,
              after = "{'count': #request.links().size()}")
    public List<TopLink> replace(TopLinkReplaceRequest request, String adminLoginId) {
        // 검사를 먼저 다 돌린다. 지운 뒤에 걸리면 롤백되긴 하지만, 틀린 요청 하나가
        // 표를 비웠다 되돌리는 일을 굳이 시킬 이유가 없다.
        List<TopLink> links = new ArrayList<>();
        List<TopLinkReplaceRequest.Item> items = request.links();
        for (int position = 0; position < items.size(); position++) {
            links.add(toLink(items.get(position), position, adminLoginId));
        }

        linkRepository.deleteAllInBatch();
        linkRepository.saveAll(links);

        log.info("[TOPLINK] 상단 띠 링크 {}개로 교체. by={}", links.size(), adminLoginId);
        return links;
    }

    private TopLink toLink(TopLinkReplaceRequest.Item item, int position, String by) {
        String where = (position + 1) + "번째 링크";
        if (item == null) {
            throw invalid(where + "가 비어 있습니다.");
        }

        String label = strip(item.label());
        if (label.isEmpty()) {
            // 아이콘이 있어도 받는다 — 이름 없는 그림 버튼은 화면 낭독기에 "링크"라고만 읽힌다.
            throw invalid(where + "에 이름이 없습니다. 아이콘만 쓰는 링크에도 이름을 적어 주세요.");
        }
        if (label.length() > MAX_LABEL) {
            throw invalid(where + "의 이름이 너무 깁니다. " + MAX_LABEL + "자까지 적을 수 있습니다.");
        }

        String href = strip(item.href());
        if (href.isEmpty()) {
            throw invalid(where + "(" + label + ")에 주소가 없습니다.");
        }
        if (href.length() > MAX_HREF) {
            throw invalid(where + "(" + label + ")의 주소가 너무 깁니다.");
        }
        if (!EXTERNAL.matcher(href).matches() && !INTERNAL.matcher(href).matches()) {
            throw invalid(where + "(" + label + ")의 주소는 https:// 로 시작해야 합니다.");
        }

        String iconKey = strip(item.iconKey());
        if (!iconKey.isEmpty() && !ICON_KEY.matcher(iconKey).matches()) {
            throw invalid(where + "(" + label + ")의 아이콘을 다시 올려 주세요.");
        }

        return TopLink.of(label, href, iconKey.isEmpty() ? null : iconKey, position, by);
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT, message);
    }
}
