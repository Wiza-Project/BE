package com.gnagnoohc.scms.domain.career.dto.resume;

import lombok.Builder;

import java.time.OffsetDateTime;

@Builder
public record ResumeResponseDTO(
        Integer careerDocumentId,
        String documentTitle,
        Integer versionNo,
        ResumeContentDTO contentData,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
