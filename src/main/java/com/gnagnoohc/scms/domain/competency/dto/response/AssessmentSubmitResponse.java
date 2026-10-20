package com.gnagnoohc.scms.domain.competency.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AssessmentSubmitResponse(
        Integer attemptId,
        String attemptStatus,
        Instant submittedAt,
        List<CompetencyScore> scores
) {
    public record CompetencyScore(
            Integer competencyId,
            String competencyName,
            Integer displayOrder,
            BigDecimal rawScore,
            BigDecimal convertedScore
    ) {}
}
