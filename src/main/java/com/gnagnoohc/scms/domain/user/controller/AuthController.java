package com.gnagnoohc.scms.domain.user.controller;

import com.gnagnoohc.scms.domain.user.dto.LoginRequest;
import com.gnagnoohc.scms.domain.user.dto.LoginResponse;
import com.gnagnoohc.scms.domain.user.service.AuthResult;
import com.gnagnoohc.scms.domain.user.service.AuthService;
import com.gnagnoohc.scms.global.common.dto.ApiResponse;
import com.gnagnoohc.scms.global.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그인 / 토큰 재발급 / 로그아웃.
 *
 * Access 토큰은 JSON 응답 바디로, Refresh 토큰은 httpOnly 쿠키로 내려줍니다
 * (결정 배경은 AuthService 상단 주석 참고). 쿠키는 이 컨트롤러 경로에서만 쓰이므로
 * path를 /api/auth 로 제한합니다.
 *
 * 30분 미활동 자동 로그아웃: Access 토큰 유효기간(app.jwt.access-token-validity-seconds,
 * 기본 30분)이 곧 그 기준입니다. 프론트가 사용자 활동을 감지하는 동안에만 만료 직전에
 * 이 reissue를 호출해 세션을 이어가고, 활동이 없으면 Access 토큰이 그대로 만료되어
 * API 요청이 401로 실패합니다 — 프론트는 그 시점에 세션을 정리합니다.
 */
@Tag(name = "Auth", description = "로그인 / 토큰 재발급 / 로그아웃")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    /** 로컬 개발(http)에서는 false, 운영(https)에서는 true 로 설정하세요. */
    @Value("${app.jwt.cookie-secure:true}")
    private boolean cookieSecure;

    @Operation(summary = "로그인", description = "성공 시 Access 토큰은 바디로, Refresh 토큰은 httpOnly 쿠키로 내려줍니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authService.login(request.universityNo(), request.password());
        return withRefreshCookie(result);
    }

    @Operation(summary = "토큰 재발급", description = "httpOnly 쿠키의 Refresh 토큰으로 새 Access/Refresh 토큰을 발급합니다.")
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<LoginResponse>> reissue(HttpServletRequest request) {
        String refreshToken = readRefreshCookie(request);
        AuthResult result = authService.reissue(refreshToken);
        return withRefreshCookie(result);
    }

    @Operation(summary = "로그아웃", description = "Refresh 토큰 쿠키를 제거합니다. Access 토큰은 서버가 별도로 무효화하지 않으므로 프론트에서 즉시 폐기")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        ResponseCookie expired = buildRefreshCookie("", 0);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expired.toString())
                .body(ApiResponse.ok());
    }

    private ResponseEntity<ApiResponse<LoginResponse>> withRefreshCookie(AuthResult result) {
        ResponseCookie cookie = buildRefreshCookie(
                result.refreshToken(),
                jwtTokenProvider.getRefreshTokenValiditySeconds()
        );
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.ok(result.body()));
    }

    private ResponseCookie buildRefreshCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (REFRESH_TOKEN_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
