package com.gnagnoohc.scms.domain.program.service;

import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

// 비교과프로그램 QR 자기출석체크용 단기 토큰 발급/검증.
//
// QR 자체는 DB에 저장하지 않는다. "이 세션(programId+sessionId)용, O시각까지만 유효하다"는 정보를 서명해
// 문자열(JWT)로 만들어두면, 검증은 서명만 확인하면 되므로 별도 테이블/조회 없이도 위변조·만료 여부를 알 수 있다.
// global.security.JwtTokenProvider는 로그인 토큰(access/refresh) 전용이고 program 도메인 밖의 파일이라
// 그대로 재사용하거나 수정하지 않고, 같은 app.jwt.secret 설정값만 공유해 이 클래스를 독립적으로 둔다.
@Component
public class ProgramAttendanceQrTokenService {

    // 스태프가 QR을 화면에 띄운 뒤 학생들이 스캔할 시간을 감안한 유효시간. 너무 길게 잡으면 뒤늦게 스캔한
    // 학생이 이전 화면을 사진으로 찍어 부정 출석하는 데 악용될 여지가 커지므로 짧게(5분) 잡는다.
    private static final long VALIDITY_SECONDS = 5 * 60;
    private static final String CLAIM_PROGRAM_ID = "programId";
    private static final String CLAIM_SESSION_ID = "sessionId";

    private final SecretKey key;

    public ProgramAttendanceQrTokenService(@Value("${app.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issue(Integer programId, Integer sessionId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(VALIDITY_SECONDS);
        String token = Jwts.builder()
                .claim(CLAIM_PROGRAM_ID, programId)
                .claim(CLAIM_SESSION_ID, sessionId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    // 서명/만료를 검증하고, 토큰에 담긴 programId·sessionId가 지금 학생이 체크인하려는 화면의 것과 일치하는지까지
    // 확인한다. 서명 위조, 만료, 다른 세션의 QR을 잘못 스캔한 경우 모두 QR_TOKEN_INVALID 하나로 통일해서 던진다
    // (어떤 이유인지 세분화해 알려주면 공격자가 토큰 구조를 추측하는 데 도움이 될 뿐이라 굳이 나누지 않는다).
    public void verify(String token, Integer programId, Integer sessionId) {
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.QR_TOKEN_INVALID);
        }
        Integer tokenProgramId = claims.get(CLAIM_PROGRAM_ID, Integer.class);
        Integer tokenSessionId = claims.get(CLAIM_SESSION_ID, Integer.class);
        if (!programId.equals(tokenProgramId) || !sessionId.equals(tokenSessionId)) {
            throw new BusinessException(ErrorCode.QR_TOKEN_INVALID);
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
