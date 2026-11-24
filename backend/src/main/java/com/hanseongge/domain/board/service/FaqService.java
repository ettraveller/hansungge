package com.hanseongge.domain.board.service;

import com.hanseongge.domain.board.dto.FaqReplaceRequest;
import com.hanseongge.domain.board.entity.Faq;
import com.hanseongge.domain.board.repository.FaqRepository;
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
 * 자주 묻는 질문.
 *
 * <h2>통째로 갈아 끼운다</h2>
 * 상단 띠 링크·메인 배너와 같은 방식이다. 질문은 차례가 뜻을 가지는 묶음이라, 한 건씩
 * 고치고 옮기는 길을 두면 차례를 바꾸는 도중의 목록이 손님 화면에 걸린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FaqService {

    static final int MAX_CATEGORY = 50;
    static final int MAX_QUESTION = 300;
    static final int MAX_ANSWER = 4000;

    private final FaqRepository faqRepository;

    /** 손님 화면용 — 내건 것만 */
    @Transactional(readOnly = true)
    public List<Faq> findActive() {
        return faqRepository.findByActiveTrueOrderBySortAscIdAsc();
    }

    /** 관리자 화면용 — 꺼 둔 것까지 */
    @Transactional(readOnly = true)
    public List<Faq> findAll() {
        return faqRepository.findAllByOrderBySortAscIdAsc();
    }

    @Transactional
    @AuditLog(action = AuditActions.FAQ_REPLACE,
              targetType = AuditActions.TARGET_FAQ,
              after = "{'count': #request.faqs().size()}")
    public List<Faq> replace(FaqReplaceRequest request, String adminLoginId) {
        // 검사를 먼저 다 돌린 뒤에 지운다. 한 건이 틀렸다고 있던 질문이 사라지면 안 된다.
        List<Faq> faqs = new ArrayList<>();
        List<FaqReplaceRequest.Item> items = request.faqs();
        for (int position = 0; position < items.size(); position++) {
            faqs.add(toFaq(items.get(position), position));
        }

        faqRepository.deleteAllInBatch();
        faqRepository.saveAll(faqs);

        log.info("[FAQ] 자주 묻는 질문 {}건으로 교체. by={}", faqs.size(), adminLoginId);
        return faqs;
    }

    private Faq toFaq(FaqReplaceRequest.Item item, int position) {
        String where = (position + 1) + "번째 질문";
        if (item == null) {
            throw invalid(where + "이 비어 있습니다.");
        }
        String category = strip(item.category());
        String question = strip(item.question());
        String answer = strip(item.answer());

        if (category.isEmpty()) {
            throw invalid(where + "에 묶음 이름이 없습니다. (예: 주문·결제)");
        }
        if (category.length() > MAX_CATEGORY) {
            throw invalid(where + "의 묶음 이름이 너무 깁니다. " + MAX_CATEGORY + "자까지 적을 수 있습니다.");
        }
        if (question.isEmpty()) {
            throw invalid(where + "에 질문이 없습니다.");
        }
        if (question.length() > MAX_QUESTION) {
            throw invalid(where + "의 질문이 너무 깁니다. " + MAX_QUESTION + "자까지 적을 수 있습니다.");
        }
        if (answer.isEmpty()) {
            throw invalid(where + "(" + question + ")에 답이 없습니다.");
        }
        if (answer.length() > MAX_ANSWER) {
            throw invalid(where + "의 답이 너무 깁니다. " + MAX_ANSWER + "자까지 적을 수 있습니다.");
        }
        return Faq.of(category, question, answer, position, item.active() == null || item.active());
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_INPUT, message);
    }
}
