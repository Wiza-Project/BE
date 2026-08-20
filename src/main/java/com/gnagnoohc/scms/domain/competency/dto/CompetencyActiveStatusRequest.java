package com.gnagnoohc.scms.domain.competency.dto;

import jakarta.validation.constraints.NotNull;

public record CompetencyActiveStatusRequest(
        @NotNull(message = "사용여부는 필수입니다.")
        Boolean active
) {}
