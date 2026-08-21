package com.gnagnoohc.scms.domain.program.dto.response;

import com.gnagnoohc.scms.domain.competency.entity.Competency;

/** 프로그램 등록 폼의 핵심역량 드롭다운용 응답 DTO. */

public record CompetencyOptionResponseDTO(
        Integer competencyId,
        String competencyCode,
        String competencyName,
        Integer displayOrder
) {
    public static CompetencyOptionResponseDTO from(Competency competency) {
        return new CompetencyOptionResponseDTO(
                competency.getCompetencyId(),
                competency.getCompetencyCode(),
                competency.getCompetencyName(),
                competency.getDisplayOrder()
        );
    }
}
