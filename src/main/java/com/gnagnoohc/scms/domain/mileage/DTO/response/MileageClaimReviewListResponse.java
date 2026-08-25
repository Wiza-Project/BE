package com.gnagnoohc.scms.domain.mileage.DTO.response;

import com.gnagnoohc.scms.domain.mileage.entity.ExternalActivityClaim;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 교직원 마일리지 심사 목록 한 건에 필요한 응답이다. */
public record MileageClaimReviewListResponse(
        Integer externalClaimId,
        Integer studentId,
        String studentName,
        String studentNo,
        Integer activityTypeId,
        String activityTypeName,
        String activityName,
        LocalDate activityDate,
        BigDecimal requestedPoints,
        BigDecimal policyPoints,
        String claimStatus,
        Instant applicationDate,
        boolean hasEvidence
) {

    public static MileageClaimReviewListResponse from(ExternalActivityClaim claim) {
        return new MileageClaimReviewListResponse(
                claim.getExternalClaimId(),
                claim.getStudent().getUserId(),
                claim.getStudent().getUserName(),
                claim.getStudent().getUniversityNo(),
                claim.getActivityType().getActivityTypeId(),
                claim.getActivityType().getActivityName(),
                claim.getActivityName(),
                claim.getActivityDate(),
                claim.getRequestedPoints(),
                claim.getMileagePolicy() == null ? null : claim.getMileagePolicy().getPoints(),
                claim.getClaimStatus(),
                claim.getCreatedAt(),
                claim.getFileGroup() != null
        );
    }
}
