package com.gnagnoohc.scms.domain.program.dto.application;

import jakarta.validation.constraints.NotBlank;

/**
 * 비교과프로그램 참여 신청 "반려" 요청 DTO. 반려 사유는 반드시 입력해야 하며,
 * 공백만 있거나 아예 없으면 @NotBlank 검증에 걸려 400 응답이 내려간다.
 */
public record ProgramApplicationRejectRequestDTO(
        @NotBlank(message = "반려 사유는 필수입니다.") String reason
) {
}
