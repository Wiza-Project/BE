package com.gnagnoohc.scms.global.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QueryDSL 전역 설정 클래스
 *
 * <p><strong>[설정 목적]</strong></p>
 * <ul>
 *   <li>JPA EntityManager를 기반으로 JPAQueryFactory를 스프링 빈(Bean)으로 등록</li>
 *   <li>도메인별 커스텀 레포지토리 구현체(Impl)에서 생성자 주입(@RequiredArgsConstructor)으로 활용 가능</li>
 * </ul>
 */
@Configuration
public class QuerydslConfig {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * QueryDSL JPAQueryFactory 빈 등록
     */
    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(entityManager);
    }
}
