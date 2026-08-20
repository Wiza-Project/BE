package com.gnagnoohc.scms.domain.competency.dto;

import com.gnagnoohc.scms.domain.competency.entity.Competency;

public record CompetencyResponse(
        Integer competencyId,
        String competencyCode,
        String competencyName,
        String englishName,
        String description,
        Integer displayOrder,
        boolean active
) {
    public static CompetencyResponse from(Competency competency) {
        return new CompetencyResponse(
                competency.getCompetencyId(),
                competency.getCompetencyCode(),
                competency.getCompetencyName(),
                competency.getEnglishName(),
                competency.getDescription(),
                competency.getDisplayOrder(),
                competency.isActive()
        );
    }
}
