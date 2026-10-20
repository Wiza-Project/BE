package com.gnagnoohc.scms.global.security;

import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/**
 * SecurityConfig.authorizeHttpRequests() (hasRole/authenticated) 에서 걸러진 요청은
 * DispatcherServlet 이전 필터 체인에서 끝나버려 GlobalExceptionHandler(@RestControllerAdvice)를
 * 타지 않는다. 커스텀 엔트리포인트/핸들러를 등록하지 않으면 formLogin/httpBasic이 둘 다
 * disable된 상태라 Spring Security 기본값이 나가는데, 그게 하필
 *   - 미인증(토큰 없음/만료)도 401이 아니라 403
 *   - 바디도 비어있음 (다른 모든 에러 응답이 쓰는 ApiResponse 형태가 아님)
 * 이라서 프론트의 401 감지(자동 재발급) 로직도, 공통 에러 파싱 로직도 못 탄다.
 * 그래서 여기서 다른 에러 응답과 동일한 모양으로 직접 내려준다.
 */
@Component
@RequiredArgsConstructor
public class SecurityResponseHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /** 로그인 자체가 안 된 경우 (토큰 없음/만료/위조) */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        write(response, ErrorCode.UNAUTHORIZED);
    }

    /** 로그인은 됐지만 권한(hasRole 등)이 부족한 경우 */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        write(response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.fail(errorCode.getCode(), errorCode.getMessage())));
    }
}
