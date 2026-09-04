package com.gnagnoohc.scms.domain.mileage.DTO.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.mileage.entity.ExternalActivityClaim;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 학생이 제출한 외부활동 마일리지 신청 결과다. */
public record MileageExternalActivityClaimResponse(
        Integer externalClaimId,
        Integer activityTypeId,
        String activityTypeName,
        Integer mileagePolicyId,
        BigDecimal policyPoints,
        String activityName,
        LocalDate activityDate,
        BigDecimal requestedPoints,
        JsonNode detailData,
        Integer fileGroupId,
        String claimStatus,
        Instant applicationDate
) {

    public static MileageExternalActivityClaimResponse from(ExternalActivityClaim claim) {
        MileagePolicy policy = claim.getMileagePolicy();
        return new MileageExternalActivityClaimResponse(
                claim.getExternalClaimId(),
                claim.getActivityType().getActivityTypeId(),
                claim.getActivityType().getActivityName(),
                policy == null ? null : policy.getMileagePolicyId(),
                policy == null ? null : policy.getPoints(),
                claim.getActivityName(),
                claim.getActivityDate(),
                claim.getRequestedPoints(),
                claim.getDetailData(),
                claim.getFileGroup() == null ? null : claim.getFileGroup().getFileGroupId(),
                claim.getClaimStatus(),
                claim.getCreatedAt()
        );
    }
}
