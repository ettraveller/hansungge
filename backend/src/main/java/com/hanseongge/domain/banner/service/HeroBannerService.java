package com.hanseongge.domain.banner.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanseongge.domain.banner.dto.BannerContent;
import com.hanseongge.domain.banner.dto.HeroBannerReplaceRequest;
import com.hanseongge.domain.banner.entity.BannerKind;
import com.hanseongge.domain.banner.entity.HeroBanner;
import com.hanseongge.domain.banner.repository.HeroBannerRepository;
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

/**
 * 메인 배너.
 *
 * <h2>통째로 갈아 끼운다</h2>
 * 한 장씩 더하고 지우는 길을 두지 않는다. 배너는 묶음으로 바뀌는 것이라, 세 장을 각각
 * 지우고 각각 넣게 하면 그 사이에 <b>반쯤 섞인 배너</b>가 손님 화면에 걸린다.
 * 한 트랜잭션에서 전부 지우고 전부 다시 넣는다.
 *
 * <p>기존 행을 고치지 않고 지웠다 넣는 이유는, 이미지 키가 바뀌면 사실상 다른 배너라
 * id 를 이어 줄 의미가 없기 때문이다. 배너에는 그 id 를 가리키는 다른 데이터도 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HeroBannerService {

    private final HeroBannerRepository bannerRepository;
    private final BannerContentValidator contentValidator;
    private final ObjectMapper objectMapper;

    /** 손님 화면용. 내걸린 것만, 정해진 차례로. */
    @Transactional(readOnly = true)
    public List<HeroBanner> findActive() {
        return bannerRepository.findByActiveTrueOrderBySortAscIdAsc();
    }

    /** 관리자 화면용. 꺼 둔 것까지 본다. */
    @Transactional(readOnly = true)
    public List<HeroBanner> findAll() {
        return bannerRepository.findAllByOrderBySortAscIdAsc();
    }

    @Transactional
    @AuditLog(action = AuditActions.HERO_BANNER_REPLACE,
              targetType = AuditActions.TARGET_HERO_BANNER,
              after = "{'count': #request.banners().size()}")
    public List<HeroBanner> replace(HeroBannerReplaceRequest request, String adminLoginId) {
        bannerRepository.deleteAllInBatch();

        List<HeroBanner> saved = new ArrayList<>();
        List<HeroBannerReplaceRequest.Item> items = request.banners();
        for (int position = 0; position < items.size(); position++) {
            HeroBannerReplaceRequest.Item item = items.get(position);
            saved.add(toBanner(item, position, adminLoginId));
        }
        bannerRepository.saveAll(saved);

        log.info("[BANNER] 메인 배너 {}장으로 교체. by={}", saved.size(), adminLoginId);
        return saved;
    }

    /**
     * 꾸민 배너의 내용을 돌려준다. 사진 배너면 {@code null}.
     *
     * <p>읽다 실패해도 터지지 않는다 — DB 의 JSON 이 손으로 고쳐졌을 때 배너 한 장
     * 때문에 메인 전체가 500 으로 떨어지면, 정작 고쳐야 할 사람이 관리자 화면에도
     * 들어가지 못한다. 그 배너만 조용히 빠진다.
     */
    public BannerContent contentOf(HeroBanner banner) {
        if (banner.getKind() != BannerKind.DESIGN || banner.getContent() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(banner.getContent(), BannerContent.class);
        } catch (JsonProcessingException e) {
            log.error("[BANNER] 배너 내용을 읽지 못했습니다. id={}", banner.getId(), e);
            return null;
        }
    }

    private HeroBanner toBanner(HeroBannerReplaceRequest.Item item, int position, String by) {
        if (item.kind() == BannerKind.DESIGN) {
            // 다듬은 값만 저장한다. 받은 그대로 넣으면 손님 화면의 style 로 그대로 나간다.
            BannerContent content = contentValidator.normalize(item.content());
            return HeroBanner.ofDesign(write(content), item.altText(), position, by);
        }
        if (item.imageKey() == null || item.imageKey().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    (position + 1) + "번째 배너에 이미지가 없습니다.");
        }
        return HeroBanner.ofImage(item.imageKey(), item.mobileImageKey(), item.linkHref(),
                item.altText(), position, by);
    }

    private String write(BannerContent content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "배너 내용을 저장하지 못했습니다.");
        }
    }
}
