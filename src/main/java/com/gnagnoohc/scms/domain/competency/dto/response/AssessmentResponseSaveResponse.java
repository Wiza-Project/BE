package com.gnagnoohc.scms.domain.competency.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record AssessmentResponseSaveResponse(
        Integer questionId,
        BigDecimal selectedValue,
        Instant savedAt,
        AssessmentResponseProgress progress
) {}
