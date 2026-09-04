package com.gnagnoohc.scms.domain.mileage.DTO.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 학생이 외부활동 마일리지 신청을 제출할 때 보내는 요청이다. */
public record MileageExternalActivityClaimRequest(
        @NotNull @Positive Integer activityTypeId,
        @NotBlank @Size(max = 200) String activityName,
        @NotNull LocalDate activityDate,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal requestedPoints,
        JsonNode detailData,
        @NotNull @Positive Integer fileGroupId
) {
}
