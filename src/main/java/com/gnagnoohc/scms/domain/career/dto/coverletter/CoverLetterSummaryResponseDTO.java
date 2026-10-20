package com.gnagnoohc.scms.domain.career.dto.coverletter;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 자기소개서 목록(버전 이력) 조회 요약 응답 DTO
 */
@Getter
@Builder
@Schema(description = "자기소개서 목록 요약 응답 DTO")
public class CoverLetterSummaryResponseDTO {

    @Schema(description = "문서 식별자 PK", example = "1")
    private Integer careerDocumentId;

    @Schema(description = "자기소개서 제목", example = "2026 하반기 공채 자기소개서")
    private String documentTitle;

    @Schema(description = "버전 번호", example = "1")
    private Integer versionNo;

    @Schema(description = "AI 도구 활용 여부", example = "false")
    private boolean aiAssistanceUsed;

    @Schema(description = "최종 수정 일시 (KST)")
    private OffsetDateTime updatedAt;
}
