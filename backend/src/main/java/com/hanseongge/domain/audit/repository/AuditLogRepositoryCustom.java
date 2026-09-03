package com.hanseongge.domain.audit.repository;

import com.hanseongge.domain.audit.dto.AuditLogSearchCondition;
import com.hanseongge.domain.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** 조건이 동적으로 붙는 감사로그 검색. 구현은 QueryDSL. */
public interface AuditLogRepositoryCustom {

    Page<AuditLog> search(AuditLogSearchCondition condition, Pageable pageable);
}
