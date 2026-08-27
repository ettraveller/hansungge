package com.hanseongge.domain.audit.repository;

import com.hanseongge.domain.audit.dto.AuditLogSearchCondition;
import com.hanseongge.domain.audit.entity.AuditLog;
import com.hanseongge.domain.audit.entity.QAuditLog;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

/**
 * 감사로그 동적 검색 (QueryDSL).
 *
 * <p>JPQL 로 {@code (:actor IS NULL OR a.actor = :actor)} 식을 쓰지 않는 이유:
 * PostgreSQL 은 NULL 파라미터의 타입을 추론하지 못해 "could not determine data type"
 * 으로 실패하는 경우가 있다. 조건을 아예 붙이지 않는 편이 SQL 도 짧다.
 */
@RequiredArgsConstructor
public class AuditLogRepositoryImpl implements AuditLogRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<AuditLog> search(AuditLogSearchCondition condition, Pageable pageable) {
        QAuditLog log = QAuditLog.auditLog;
        BooleanBuilder where = buildWhere(condition, log);

        List<AuditLog> content = queryFactory
                .selectFrom(log)
                .where(where)
                // 감사로그는 최신순 고정이다. 정렬을 클라이언트가 정하게 두면
                // 인덱스(actor, created_at DESC)를 벗어난 정렬로 전건 스캔이 난다.
                .orderBy(log.createdAt.desc(), log.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // count 는 결과가 한 페이지에 다 들어가면 생략된다 (PageableExecutionUtils).
        JPAQuery<Long> countQuery = queryFactory
                .select(log.count())
                .from(log)
                .where(where);

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanBuilder buildWhere(AuditLogSearchCondition c, QAuditLog log) {
        BooleanBuilder where = new BooleanBuilder();

        if (c == null) {
            return where;
        }
        if (c.hasActor()) {
            // 부분 일치. 운영자가 'admin' 만 기억하고 'admin_kim' 을 찾는 경우가 흔하다.
            where.and(log.actor.containsIgnoreCase(c.actor().trim()));
        }
        if (c.actorType() != null) {
            where.and(log.actorType.eq(c.actorType()));
        }
        if (c.hasAction()) {
            // 행위는 코드값이므로 정확 일치. 부분 일치로 두면
            // 'ADMIN_LOGIN' 검색에 'ADMIN_LOGIN_FAILED' 가 섞여 나온다.
            where.and(log.action.eq(c.action().trim()));
        }
        if (c.hasTargetType()) {
            where.and(log.targetType.eq(c.targetType().trim()));
        }
        if (c.hasTargetId()) {
            where.and(log.targetId.eq(c.targetId().trim()));
        }
        where.and(period(c, log));
        return where;
    }

    /** 기간은 [from, to) 반열림. to 를 포함으로 두면 마지막 날이 통째로 누락된다. */
    private BooleanExpression period(AuditLogSearchCondition c, QAuditLog log) {
        if (c.from() != null && c.to() != null) {
            return log.createdAt.goe(c.from()).and(log.createdAt.lt(c.to()));
        }
        if (c.from() != null) {
            return log.createdAt.goe(c.from());
        }
        if (c.to() != null) {
            return log.createdAt.lt(c.to());
        }
        return null;
    }
}
