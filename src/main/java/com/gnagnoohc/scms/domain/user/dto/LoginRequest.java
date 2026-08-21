package com.gnagnoohc.scms.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "아이디를 입력해주세요.")
        @Pattern(regexp = "\\d{8}", message = "아이디는 숫자 8자리로 입력해주세요.")
        String universityNo,
        @NotBlank(message = "비밀번호를 입력해주세요.")
        // 비밀번호 정책(최소 길이/특수문자 등)은 가입·변경 시점의 몫이라 로그인에는 걸지 않습니다.
        // 이 상한선은 정책이 아니라 방어용입니다 — BCrypt가 72바이트 초과 입력을 조용히 잘라
        // 비교하는 특성이 있어, 과도하게 긴 페이로드가 그대로 들어오는 것만 막아둡니다.
        @Size(max = 100, message = "비밀번호가 너무 깁니다.")
        String password
) {
}
