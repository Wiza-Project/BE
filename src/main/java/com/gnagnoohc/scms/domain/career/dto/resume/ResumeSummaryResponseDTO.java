package com.gnagnoohc.scms.domain.career.dto.resume;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record ResumeSummaryResponseDTO(
        Integer careerDocumentId,
        String documentTitle,
        Integer versionNo,
        OffsetDateTime updatedAt
) {
}
