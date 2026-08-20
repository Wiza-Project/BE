package com.gnagnoohc.scms.domain.user.dto;

/**
 * 로그인 / 토큰 재발급(reissue) 공통 응답 바디.
 * Refresh 토큰은 이 바디에 담지 않고 httpOnly 쿠키로만 내려줍니다 (AuthController 참고).
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserSummaryResponse user
) {
    public static LoginResponse of(String accessToken, long expiresIn, UserSummaryResponse user) {
        return new LoginResponse(accessToken, "Bearer", expiresIn, user);
    }
}
