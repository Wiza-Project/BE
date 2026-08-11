package com.gnagnoohc.scms.global.config;

import com.gnagnoohc.scms.global.security.AuthUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * BaseTimeEntity 의 createdAt/updatedAt,
 * BaseEntity 의 createdBy/updatedBy 자동 기록을 활성화합니다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()
                    || !(auth.getPrincipal() instanceof AuthUser authUser)) {
                return Optional.of("SYSTEM");
            }
            return Optional.of(authUser.getLoginId());
        };
    }
}
