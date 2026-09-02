package com.gnagnoohc.scms.domain.competency.dto.response;

import java.math.BigDecimal;

public record AssessmentAttendanceResponse(
        Integer assessmentRoundId,
        long targetCount,
        long completedCount,
        BigDecimal attendanceRate
) {}
