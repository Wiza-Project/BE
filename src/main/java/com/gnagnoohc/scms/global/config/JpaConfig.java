package com.gnagnoohc.scms.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * BaseTimeEntity 의 createdAt/updatedAt,
 * BaseEntity 의 createdBy/updatedBy 자동 기록을 활성화합니다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
