package com.gnagnoohc.scms.domain.competency.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CompetencyRegisterRequest(
        @NotBlank(message = "핵심역량명은 필수입니다.") String competencyName,
        String englishName,
        String description
) {}
