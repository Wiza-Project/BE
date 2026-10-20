package com.gnagnoohc.scms.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 발급 / 검증.
 *
 * 토큰 발급·갱신 정책(만료시간)을 확정해서 활성화합니다 — 요구사항: 30분 미활동 자동 로그아웃.
 * Access 토큰 수명 자체를 30분으로 잡아, 활동 중엔 AuthService.reissue() 로 조용히 갱신하고
 * 활동이 없으면 그대로 만료되도록 합니다.
 */
@Slf4j
@Component
@Getter
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenValiditySeconds;
    private final long refreshTokenValiditySeconds;

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-validity-seconds}") long accessTokenValiditySeconds,
            @Value("${app.jwt.refresh-token-validity-seconds}") long refreshTokenValiditySeconds
    ) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValiditySeconds = accessTokenValiditySeconds;
        this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    }

    public String createAccessToken(AuthUser user) {
        return build(user, accessTokenValiditySeconds, "access");
    }

    public String createRefreshToken(AuthUser user) {
        return build(user, refreshTokenValiditySeconds, "refresh");
    }

    private String build(AuthUser user, long validitySeconds, String tokenType) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validitySeconds * 1000);
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("universityNo", user.getUniversityNo())
                .claim("userType", user.getUserType())
                .claim("departmentCodeId", user.getDepartmentCodeId())
                .claim("tokenType", tokenType)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Integer parseUserId(String token) {
        Claims claims = parseClaims(token);
        return claims == null ? null : Integer.valueOf(claims.getSubject());
    }

    /** 서명이 유효하고 만료되지 않은 refresh 토큰인지 (access 토큰을 재발급에 재사용하는 것을 막기 위함) */
    public boolean isRefreshToken(String token) {
        Claims claims = parseClaims(token);
        return claims != null && "refresh".equals(claims.get("tokenType", String.class));
    }

    /** 서명이 유효하고 만료되지 않은 access 토큰인지 (refresh 토큰을 API 인증에 재사용하는 것을 막기 위함) */
    public boolean isAccessToken(String token) {
        Claims claims = parseClaims(token);
        return claims != null && "access".equals(claims.get("tokenType", String.class));
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException exception) {
            log.debug("만료된 토큰");
            return null;
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("유효하지 않은 토큰: {}", exception.getMessage());
            return null;
        }
    }
}
