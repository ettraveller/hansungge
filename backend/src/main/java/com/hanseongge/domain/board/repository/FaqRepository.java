package com.hanseongge.domain.board.repository;

import com.hanseongge.domain.board.entity.Faq;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    /** 관리자 화면용. 꺼 둔 것까지 */
    List<Faq> findAllByOrderBySortAscIdAsc();

    /** 손님 화면용. 내건 것만 */
    List<Faq> findByActiveTrueOrderBySortAscIdAsc();
}
