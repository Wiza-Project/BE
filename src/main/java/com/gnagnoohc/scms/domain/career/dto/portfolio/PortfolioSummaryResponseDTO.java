package com.gnagnoohc.scms.domain.career.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 포트폴리오 목록 조회 요약 응답 DTO
 */
@Getter
@Builder
@Schema(description = "포트폴리오 목록 요약 응답 DTO")
public class PortfolioSummaryResponseDTO {

    @Schema(description = "문서 식별자 PK", example = "1")
    private Integer careerDocumentId;

    @Schema(description = "포트폴리오 항목 제목", example = "졸업작품 - 학사관리 시스템")
    private String documentTitle;

    @Schema(description = "항목 순번(버전 번호 필드를 재사용)", example = "1")
    private Integer versionNo;

    // boolean(primitive)로 두면 Lombok의 isXxx 게터를 Jackson이 "is" 접두어를 벗겨 "public" 키로 직렬화한다.
    // 요청 DTO(PortfolioVisibilityRequestDTO)와 JSON 키를 "isPublic"으로 맞추기 위해 Boolean(래퍼)을 사용한다.
    @Schema(description = "공개 여부", example = "false")
    private Boolean isPublic;

    @Schema(description = "첨부파일 개수", example = "2")
    private int attachmentCount;

    @Schema(description = "최종 수정 일시 (KST)")
    private OffsetDateTime updatedAt;
}
