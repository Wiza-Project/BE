package com.gnagnoohc.scms.domain.user.service;

import com.gnagnoohc.scms.domain.user.dto.LoginResponse;

/**
 * AuthService 가 컨트롤러에 돌려주는 내부 결과.
 *
 * refreshToken 은 API 응답 바디(JSON)에는 절대 포함하지 않고 AuthController 가
 * httpOnly 쿠키로만 내려보냅니다. dto 패키지가 아니라 여기(service)에 둔 것도
 * 실수로 컨트롤러가 이 객체를 그대로 반환해 refreshToken 이 JSON에 노출되는 사고를
 * 막기 위함입니다 — dto.LoginResponse 에는 애초에 그 필드가 없습니다.
 */
public record AuthResult(LoginResponse body, String refreshToken) {
}
