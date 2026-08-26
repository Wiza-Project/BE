package com.gnagnoohc.scms.domain.mileage.DTO.response;

import com.gnagnoohc.scms.domain.mileage.entity.ExternalActivityClaim;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 교직원 외부활동 마일리지 심사 목록의 한 건이다. */
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
        Integer reviewedBy,
        Instant reviewedAt,
        String reviewReason,
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
                claim.getReviewedBy(),
                claim.getReviewedAt(),
                claim.getReviewReason(),
                claim.getFileGroup() != null
        );
    }
}
