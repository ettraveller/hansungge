package com.hanseongge.domain.board;

import com.hanseongge.domain.board.dto.FaqReplaceRequest;
import com.hanseongge.domain.board.dto.FaqReplaceRequest.Item;
import com.hanseongge.domain.board.entity.Faq;
import com.hanseongge.domain.board.repository.FaqRepository;
import com.hanseongge.domain.board.service.FaqService;
import com.hanseongge.global.error.BusinessException;
import com.hanseongge.support.EmbeddedPostgresSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 자주 묻는 질문 — 차례, 통째 교체, 내걸기, 빈 칸 거절. */
@SpringBootTest
class FaqIntegrationTest extends EmbeddedPostgresSupport {

    @Autowired FaqService faqService;
    @Autowired FaqRepository faqRepository;

    @AfterEach
    void cleanUp() {
        // 임베디드 PostgreSQL 은 테스트 클래스가 함께 쓴다. 남기면 다른 클래스가 본다.
        faqRepository.deleteAllInBatch();
    }

    private void replace(Item... items) {
        faqService.replace(new FaqReplaceRequest(List.of(items)), "tester");
    }

    @Test
    @DisplayName("보낸 차례대로 나가고, 앞뒤 공백은 뗀다")
    void keepsOrderAndTrims() {
        replace(new Item(" 주문·결제 ", " 무통장입금도 되나요? ", " 됩니다. ", null),
                new Item("배송", "얼마나 걸리나요?", "상품마다 다릅니다.", true));

        List<Faq> saved = faqService.findAll();

        assertThat(saved).extracting(Faq::getQuestion)
                .containsExactly("무통장입금도 되나요?", "얼마나 걸리나요?");
        assertThat(saved.get(0).getCategory()).isEqualTo("주문·결제");
        assertThat(saved.get(0).getAnswer()).isEqualTo("됩니다.");
        // 내걸기를 적지 않으면 내건 것으로 본다
        assertThat(saved.get(0).isActive()).isTrue();
    }

    @Test
    @DisplayName("꺼 둔 질문은 손님 화면에 나가지 않는다")
    void hidesInactiveFromPublic() {
        replace(new Item("배송", "보이는 질문", "답", true),
                new Item("배송", "꺼 둔 질문", "답", false));

        assertThat(faqService.findActive()).extracting(Faq::getQuestion).containsExactly("보이는 질문");
        assertThat(faqService.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("다시 저장하면 보낸 것이 곧 전체다 — 빈 목록이면 모두 내린다")
    void replacesWhole() {
        replace(new Item("배송", "첫째", "답", true), new Item("배송", "둘째", "답", true));

        replace(new Item("배송", "둘째", "답", true));
        assertThat(faqService.findAll()).extracting(Faq::getQuestion).containsExactly("둘째");

        replace();
        assertThat(faqService.findAll()).isEmpty();
    }

    @Test
    @DisplayName("빈 칸이 있으면 거절하고, 있던 질문은 그대로 둔다")
    void rejectsBlankAndKeepsExisting() {
        replace(new Item("배송", "있던 질문", "답", true));

        assertThatThrownBy(() -> replace(new Item("배송", "새 질문", "   ", true)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> replace(new Item("", "새 질문", "답", true)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> replace(new Item("배송", null, "답", true)))
                .isInstanceOf(BusinessException.class);

        assertThat(faqService.findAll()).extracting(Faq::getQuestion).containsExactly("있던 질문");
    }
}
