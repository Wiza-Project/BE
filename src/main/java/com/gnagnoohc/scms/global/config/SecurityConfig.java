package com.gnagnoohc.scms.global.config;

import com.gnagnoohc.scms.global.security.JwtAuthenticationFilter;
import com.gnagnoohc.scms.global.security.SecurityResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * ── 권한 설계 메모 ───────────────────────────────────────────────
 * 이 시스템의 사용자 유형은 학생·교직원·관리자 3종이며, 화면은 학생·교직원
 * 포털만 제공합니다. 역할은 학생(SD100), 교직원(ST100)·교수(ST300)·상담사(ST200),
 * 관리자(AD100)로 구분합니다.
 *
 * 그래서 2단 구조로 갑니다.
 *   1단계: URL 패턴 + UserType   → 이 파일에서 hasRole 로 거름
 *   2단계: 부서(CommonCode) 판정 → 서비스 계층에서 부서 코드로 검사
 *
 * URL 패턴에 부서까지 욱여넣으면 규칙이 폭발합니다. 2단계를 분리하세요.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // @PreAuthorize 사용 가능
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsConfigurationSource corsConfigurationSource;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityResponseHandler securityResponseHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(securityResponseHandler)
                .accessDeniedHandler(securityResponseHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 인증 불필요
                .requestMatchers(
                    "/api/auth/**",
                    "/actuator/health",
                    "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"
                ).permitAll()

                // 학생 전용 (진단검사 응시, 상담예약, 마일리지 신청, 포트폴리오)
                .requestMatchers("/api/students/**").hasRole("STUDENT")

                // 상담사 전용 (상담일정 등록/확정/결과등록). ST200(일반 상담사)과 ST300(지도교수)이
                // 서로 배타적으로 접근한다 — 겸임(ST200+ST300)은 URL 1차 인가가 아니라
                // CounselManagementAccessPolicy(서비스 2차 인가)에서 403(A004)으로 거부한다.
                .requestMatchers("/api/counselors/**").hasAnyRole("ST200", "ST300")

                // 기업체는 현재 사용자 유형·포털 범위에서 제외한다. 향후 같은 경로의 API가
                // 실수로 추가돼도 일반 인증 사용자에게 노출되지 않게 명시적으로 차단한다.
                .requestMatchers("/api/companies/**").denyAll()

                // 관리자 전용 포털·API는 제공하지 않는다. 이전 경로가 일반 인증 사용자에게
                // 열리지 않도록 명시적으로 차단한다.
                .requestMatchers("/api/admin/**").denyAll()

                // 교직원 포털 운영 API. 부서 판정은 서비스 계층에서 추가로 수행한다.
                .requestMatchers("/api/staff/**").hasRole("STAFF")

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
