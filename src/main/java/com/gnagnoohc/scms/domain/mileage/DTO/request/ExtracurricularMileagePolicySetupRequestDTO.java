package com.gnagnoohc.scms.domain.mileage.DTO.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** 핵심역량 6개에 대한 비교과 5점 정책 일괄 구성 요청 DTO. */
public record ExtracurricularMileagePolicySetupRequestDTO(
        @NotNull @Positive Integer academicYear,
        @Size(max = 20) String semesterCode,
        @NotNull LocalDate validFrom,
        LocalDate validTo
) {
}
