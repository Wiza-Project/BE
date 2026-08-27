package com.gnagnoohc.scms.domain.mileage.DTO.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/** 학생이 마일리지 시뮬레이션에서 설정한 목표와 예정 활동이다. */
public record MileageSimulationRequest(
        @NotNull @Min(2000) @Max(9999) Integer academicYear,
        @NotBlank String semesterCode,
        Integer targetBenefitPolicyId,
        @DecimalMin(value = "0.00") @Digits(integer = 10, fraction = 2)
        BigDecimal targetPoints,
        List<@Valid PlannedActivity> plannedActivities
) {

    /** 선택한 마일리지 정책과 예정 횟수다. 실제 적립 거래를 생성하지 않는다. */
    public record PlannedActivity(
            @NotNull Integer mileagePolicyId,
            @NotNull @Min(1) Integer quantity
    ) {
    }
}
