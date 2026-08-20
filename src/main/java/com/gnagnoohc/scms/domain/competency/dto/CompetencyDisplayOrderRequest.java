package com.gnagnoohc.scms.domain.competency.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CompetencyDisplayOrderRequest(
        @NotNull(message = "축순서는 필수입니다.")
        @Min(value = 1, message = "축순서는 1~6 사이여야 합니다.")
        @Max(value = 6, message = "축순서는 1~6 사이여야 합니다.")
        Integer displayOrder
) {}
