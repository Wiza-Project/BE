package com.gnagnoohc.scms.domain.program.dto.response;

import java.time.Instant;

/**
 * 비교과프로그램 QR 출석체크용 토큰 발급 응답 DTO. 스태프 화면은 이 token 문자열을 그대로 QR 이미지로
 * 렌더링해서 화면에 띄운다(QR 이미지 생성 자체는 프론트 책임). token은 DB에 저장하지 않는 서명된
 * 단기 토큰이라, 서버는 만료시각(expiresAt)만 함께 내려줘 화면에 남은 시간을 보여줄 수 있게 한다.
 */
public record ProgramAttendanceQrTokenResponseDTO(
        String token,
        Instant expiresAt
) {
}
