package com.gnagnoohc.scms.domain.career.dto.ai;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 자연어 채용공고 추천 채팅 요청. */
public record JobChatRequestDTO(
        @NotBlank
        @Schema(description = "사용자의 자연어 취업 희망 조건", example = "서울에서 Java와 Spring을 사용하는 백엔드 신입 정규직을 추천해줘")
        String message
) {
}
