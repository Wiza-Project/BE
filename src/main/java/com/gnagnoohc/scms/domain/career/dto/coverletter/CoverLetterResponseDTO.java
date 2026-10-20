package com.gnagnoohc.scms.domain.career.dto.coverletter;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 자기소개서 단건 상세 응답 DTO
 */
@Getter
@Builder
@Schema(description = "자기소개서 상세 응답 DTO")
public class CoverLetterResponseDTO {

    @Schema(description = "문서 식별자 PK", example = "1")
    private Integer careerDocumentId;

    @Schema(description = "학생 식별자", example = "10")
    private Integer studentUserId;

    @Schema(description = "자기소개서 제목", example = "2026 하반기 공채 자기소개서")
    private String documentTitle;

    @Schema(description = "버전 번호", example = "1")
    private Integer versionNo;

    @Schema(description = "문항별 작성 내용 목록")
    private List<CoverLetterQuestionDTO> questions;

    @Schema(description = "AI 도구 활용 여부", example = "false")
    private boolean aiAssistanceUsed;

    @Schema(description = "생성 일시 (KST)")
    private OffsetDateTime createdAt;

    @Schema(description = "최종 수정 일시 (KST)")
    private OffsetDateTime updatedAt;
}
