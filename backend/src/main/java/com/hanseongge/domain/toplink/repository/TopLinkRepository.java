package com.hanseongge.domain.toplink.repository;

import com.hanseongge.domain.toplink.entity.TopLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TopLinkRepository extends JpaRepository<TopLink, Long> {

    /** 띠에 나갈 차례. */
    List<TopLink> findAllByOrderBySortAscIdAsc();
}
