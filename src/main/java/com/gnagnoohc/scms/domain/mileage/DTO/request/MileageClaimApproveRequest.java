package com.gnagnoohc.scms.domain.mileage.DTO.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/** 교직원이 외부활동 신청을 승인할 때 사용할 최종 승인 점수다. 생략하면 신청 점수를 사용한다. */
public record MileageClaimApproveRequest(
        @DecimalMin(value = "0.01", message = "승인 점수는 0보다 커야 합니다.")
        @Digits(integer = 8, fraction = 2, message = "승인 점수는 정수 8자리, 소수 2자리 이내여야 합니다.")
        BigDecimal approvedPoints
) {
}
