package com.gnagnoohc.scms.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {
    private final SecretKey key;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // 토큰 발급·갱신 정책 확정 전에는 발급 API를 활성화하지 않는다.
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
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return Integer.valueOf(claims.getSubject());
        } catch (ExpiredJwtException exception) {
            log.debug("만료된 토큰");
            return null;
        } catch (JwtException | IllegalArgumentException exception) {
            log.debug("유효하지 않은 토큰: {}", exception.getMessage());
            return null;
        }
    }
}
