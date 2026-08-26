package com.gnagnoohc.scms.domain.career.dto.coverletter;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 자기소개서 문항 1개 단위 DTO (요청/응답 공용)
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "자기소개서 문항 DTO")
public class CoverLetterQuestionDTO {

    @Schema(description = "문항 식별자 (프론트 정의값)", example = "Q1")
    private String questionId;

    @NotBlank(message = "문항 내용은 필수입니다.")
    @Schema(description = "문항 내용", example = "지원 동기")
    private String question;

    @Schema(description = "답변 본문", example = "답변 본문")
    private String answer;

    @Schema(description = "답변 글자수 (서버가 answer 길이로 계산하며, 요청 시 값은 무시된다)", example = "120")
    private Integer characterCount;
}
