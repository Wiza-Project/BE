package com.gnagnoohc.scms.domain.competency.dto.request;

import jakarta.validation.constraints.NotNull;

// true = 활성화, false = 비활성화
public record CompetencyActiveStatusRequest(
        @NotNull(message = "사용여부는 필수입니다.")
        Boolean active
) {}
