package com.gnagnoohc.scms.domain.competency.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AssessmentResponseSaveRequest(
        @NotNull(message = "응답값은 필수입니다.") BigDecimal selectedValue
) {}
