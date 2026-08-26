package com.gnagnoohc.scms.domain.career.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 포트폴리오 항목 신규 생성 요청 DTO
 */
@Getter
@NoArgsConstructor
@Schema(description = "포트폴리오 항목 생성 요청 DTO")
public class PortfolioCreateRequestDTO {

    @NotBlank(message = "제목은 필수입니다.")
    @Schema(description = "포트폴리오 항목 제목", example = "졸업작품 - 학사관리 시스템")
    private String documentTitle;

    @Schema(description = "포트폴리오 본문 (자유 JSON 구조)")
    private Map<String, Object> contentData;

    @Schema(description = "AI 도구 활용 여부 (저장만 하며 AI 기능은 구현하지 않음)", example = "false")
    private boolean aiAssistanceUsed;
}
