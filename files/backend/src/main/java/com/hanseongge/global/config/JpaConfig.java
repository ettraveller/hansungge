package com.hanseongge.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** JPA 감사(created_at / updated_at) 활성화. @see com.hanseongge.global.audit.BaseTimeEntity */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
