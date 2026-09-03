package com.hanseongge.global.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QueryDSL 설정.
 *
 * <p>상품 검색(카테고리·키워드·가격대·정렬)과 시세 이력 조회처럼 조건이 동적으로 붙는
 * 쿼리에 쓴다. 단순 조회는 Spring Data JPA 메서드로 충분하므로 QueryDSL 을 쓰지 않는다.
 */
@Configuration
public class QuerydslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
