package com.gnagnoohc.scms.domain.competency.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CompetencyDisplayOrderRequest(
        @NotNull(message = "축순서는 필수입니다.")
        @Min(value = 100, message = "축순서는 100~600 사이여야 합니다.")
        @Max(value = 600, message = "축순서는 100~600 사이여야 합니다.")
        Integer displayOrder
) {}
