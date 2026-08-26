package com.gnagnoohc.scms.domain.competency.dto;

import java.math.BigDecimal;

public record AssessmentAttendanceResponse(
        Integer assessmentRoundId,
        long targetCount,
        long completedCount,
        BigDecimal attendanceRate
) {}
