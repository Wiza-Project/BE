package com.gnagnoohc.scms.domain.program.dto.request;

import jakarta.validation.constraints.NotBlank;

// 비교과프로그램 QR 자기출석체크 요청 DTO. 학생이 스태프 화면에 뜬 QR을 스캔하면, QR에 인코딩된
// token 문자열을 그대로 이 API로 보낸다(ProgramAttendanceQrTokenService가 서명/만료를 검증한다).
public record ProgramAttendanceCheckInRequestDTO(
        @NotBlank String token
) {
}
