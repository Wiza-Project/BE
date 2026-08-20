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
 * 이 시스템은 사용자 유형이 4종(학생/교직원/상담사/기업체)이고,
 * 같은 교직원이라도 소속 부서에 따라 할 수 있는 일이 다릅니다.
 *
 * 그래서 2단 구조로 갑니다.
 *   1단계: URL 패턴 + UserType   → 이 파일에서 hasRole 로 거름
 *   2단계: 부서(Department) 판정 → 서비스 계층에서 Department 의 canXxx() 로 검사
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

                // 상담사 전용 (상담일정 등록/확정/결과등록)
                .requestMatchers("/api/counselors/**").hasRole("COUNSELOR")

                // 기업체 전용 (구인신청)
                .requestMatchers("/api/companies/**").hasRole("COMPANY")

                // 교직원 관리 화면 (부서 판정은 서비스 계층에서 추가로 수행)
                .requestMatchers("/api/admin/**").hasRole("STAFF")

                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
