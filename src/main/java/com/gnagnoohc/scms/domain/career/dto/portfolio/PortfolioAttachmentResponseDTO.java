package com.gnagnoohc.scms.domain.career.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * 포트폴리오 첨부파일 응답 DTO
 */
@Getter
@Builder
@Schema(description = "포트폴리오 첨부파일 응답 DTO")
public class PortfolioAttachmentResponseDTO {

    @Schema(description = "첨부파일 식별자 PK", example = "1")
    private Integer storedFileId;

    @Schema(description = "원본 파일명", example = "portfolio.pdf")
    private String originalFileName;

    @Schema(description = "MIME 타입", example = "application/pdf")
    private String contentType;

    @Schema(description = "파일 크기 (byte)", example = "1048576")
    private Long fileSize;
}
