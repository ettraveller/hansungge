package com.hanseongge.domain.banner.service;

import com.hanseongge.domain.banner.dto.HeroBannerReplaceRequest;
import com.hanseongge.domain.banner.entity.HeroBanner;
import com.hanseongge.domain.banner.repository.HeroBannerRepository;
import com.hanseongge.global.audit.AuditActions;
import com.hanseongge.global.audit.AuditLog;
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
            saved.add(HeroBanner.of(item.imageKey(), item.linkHref(), item.altText(),
                    position, adminLoginId));
        }
        bannerRepository.saveAll(saved);

        log.info("[BANNER] 메인 배너 {}장으로 교체. by={}", saved.size(), adminLoginId);
        return saved;
    }
}
