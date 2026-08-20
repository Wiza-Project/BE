package com.gnagnoohc.scms.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthUserService authUserService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null
                && jwtTokenProvider.isAccessToken(token)) {
            Integer userId = jwtTokenProvider.parseUserId(token);
            if (userId != null) {
                try {
                    UserDetails userDetails = authUserService.loadUserById(userId);
                    // isEnabled()/isAccountNonLocked() 는 AuthenticationManager 를 거치지 않으면
                    // 자동으로 검사되지 않으므로 여기서 직접 확인합니다. 어차피 매 요청 DB 조회를
                    // 하고 있으니(완전한 stateless는 아님), 로그인 이후 계정이
                    // LOCKED/DORMANT/WITHDRAWN 으로 바뀌었을 때 access 토큰 만료 전이라도
                    // 즉시 차단하기 위함입니다.
                    if (userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                        SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities()));
                    }
                } catch (Exception exception) {
                    SecurityContextHolder.clearContext();
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        return header != null && header.startsWith(PREFIX)
                ? header.substring(PREFIX.length()) : null;
    }
}
