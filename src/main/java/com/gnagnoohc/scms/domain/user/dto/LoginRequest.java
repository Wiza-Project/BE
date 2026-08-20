package com.gnagnoohc.scms.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "학번·교번을 입력해주세요.") String universityNo,
        @NotBlank(message = "비밀번호를 입력해주세요.") String password
) {
}
