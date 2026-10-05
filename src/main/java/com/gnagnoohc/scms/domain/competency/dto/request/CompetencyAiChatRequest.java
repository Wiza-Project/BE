package com.gnagnoohc.scms.domain.competency.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 제출 완료된 핵심역량 진단 결과에 대한 AI 해석 요청. */
public record CompetencyAiChatRequest(
        @NotNull(message = "응시 ID는 필수입니다.") @Positive(message = "응시 ID는 양수여야 합니다.") Integer attemptId,
        @NotBlank(message = "질문 내용은 필수입니다.")
        @Size(max = 3000, message = "질문은 3000자 이하로 입력해 주세요.") String message
) {}
