package com.gnagnoohc.scms.domain.mileage.DTO.response;

import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.support.MileageJsonNodeConverter;
import tools.jackson.databind.JsonNode;

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
                policy.getSemesterCode(),
                policy.getVersionNo(),
                policy.getPoints(),
                policy.getMaximumPoints(),
                policy.getValidFrom(),
                policy.getValidTo(),
                MileageJsonNodeConverter.toJackson3(policy.getDuplicateRule()),
                policy.getPolicyStatus()
        );
    }
}
