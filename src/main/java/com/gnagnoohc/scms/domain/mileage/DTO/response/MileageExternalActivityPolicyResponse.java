package com.gnagnoohc.scms.domain.mileage.DTO.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 학생 외부활동 등록 화면에서 사용할 활성 마일리지 정책 응답이다. */
public record MileageExternalActivityPolicyResponse(
        Integer mileagePolicyId,
        Integer activityTypeId,
        String activityCode,
        String activityName,
        String categoryCode,
        String earningRoute,
        Integer academicYear,
        String semesterCode,
        Integer versionNo,
        BigDecimal points,
        BigDecimal maximumPoints,
        LocalDate validFrom,
        LocalDate validTo,
        JsonNode duplicateRule,
        String policyStatus
) {

    public static MileageExternalActivityPolicyResponse from(MileagePolicy policy) {
        return new MileageExternalActivityPolicyResponse(
                policy.getMileagePolicyId(),
                policy.getActivityType().getActivityTypeId(),
                policy.getActivityType().getActivityCode(),
                policy.getActivityType().getActivityName(),
                policy.getActivityType().getCategoryCode(),
                policy.getActivityType().getEarningRoute(),
                policy.getAcademicYear(),
                policy.getSemesterCode(),
                policy.getVersionNo(),
                policy.getPoints(),
                policy.getMaximumPoints(),
                policy.getValidFrom(),
                policy.getValidTo(),
                policy.getDuplicateRule(),
                policy.getPolicyStatus()
        );
    }
}
