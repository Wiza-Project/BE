package com.gnagnoohc.scms.domain.competency.dto;

import java.time.Instant;
import java.util.Map;

public record AssessmentRoundResponse(
        Integer assessmentRoundId,
        String assessmentName,
        Integer academicYear,
        String semesterCode,
        String assessmentType,
        Instant startsAt,
        Instant endsAt,
        Map<String, Object> targetCondition,
        String roundStatus
) {}
