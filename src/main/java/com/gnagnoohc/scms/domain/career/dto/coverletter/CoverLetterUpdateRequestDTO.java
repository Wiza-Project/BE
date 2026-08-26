package com.gnagnoohc.scms.domain.career.dto.coverletter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 자기소개서 수정 요청 DTO (해당 버전 row를 그대로 갱신)
 */
@Getter
@NoArgsConstructor
@Schema(description = "자기소개서 수정 요청 DTO")
public class CoverLetterUpdateRequestDTO {

    @NotBlank(message = "제목은 필수입니다.")
    @Schema(description = "자기소개서 제목", example = "2026 하반기 공채 자기소개서")
    private String documentTitle;

    @NotEmpty(message = "문항은 최소 1개 이상이어야 합니다.")
    @Valid
    @Schema(description = "문항별 작성 내용 목록")
    private List<@NotNull @Valid CoverLetterQuestionDTO> questions;

    @Schema(description = "AI 도구 활용 여부 (저장만 하며 AI 기능은 구현하지 않음)", example = "false")
    private boolean aiAssistanceUsed;
}
