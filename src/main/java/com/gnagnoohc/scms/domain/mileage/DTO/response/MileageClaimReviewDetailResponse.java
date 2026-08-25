package com.gnagnoohc.scms.domain.mileage.DTO.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.mileage.entity.ExternalActivityClaim;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 교직원이 증빙과 정책을 확인할 때 사용하는 외부활동 마일리지 신청 상세 응답이다. */
public record MileageClaimReviewDetailResponse(
        Integer externalClaimId,
        StudentSummary student,
        ActivitySummary activity,
        PolicySummary policy,
        LocalDate activityDate,
        String activityName,
        BigDecimal requestedPoints,
        JsonNode detailData,
        Integer fileGroupId,
        String claimStatus,
        Integer reviewedBy,
        Instant reviewedAt,
        String reviewReason,
        Instant applicationDate,
        Integer mileageTransactionId,
        BigDecimal postedPoints,
        String transactionStatus
) {

    public static MileageClaimReviewDetailResponse from(
            ExternalActivityClaim claim,
            MileageTransaction transaction
    ) {
        MileageActivityType activityType = claim.getActivityType();
        MileagePolicy mileagePolicy = claim.getMileagePolicy();

        return new MileageClaimReviewDetailResponse(
                claim.getExternalClaimId(),
                new StudentSummary(
                        claim.getStudent().getUserId(),
                        claim.getStudent().getUserName(),
                        claim.getStudent().getUniversityNo()),
                activityType == null ? null : new ActivitySummary(
                        activityType.getActivityTypeId(),
                        activityType.getActivityCode(),
                        activityType.getActivityName(),
                        activityType.getCategoryCode(),
                        activityType.getEarningRoute()),
                mileagePolicy == null ? null : PolicySummary.from(mileagePolicy),
                claim.getActivityDate(),
                claim.getActivityName(),
                claim.getRequestedPoints(),
                claim.getDetailData(),
                claim.getFileGroup() == null ? null : claim.getFileGroup().getFileGroupId(),
                claim.getClaimStatus(),
                claim.getReviewedBy(),
                claim.getReviewedAt(),
                claim.getReviewReason(),
                claim.getCreatedAt(),
                transaction == null ? null : transaction.getMileageTransactionId(),
                transaction == null ? null : transaction.getPoints(),
                transaction == null ? null : transaction.getTransactionStatus()
        );
    }

    public record StudentSummary(
            Integer studentId,
            String studentName,
            String studentNo
    ) {
    }

    public record ActivitySummary(
            Integer activityTypeId,
            String activityCode,
            String activityTypeName,
            String categoryCode,
            String earningRoute
    ) {
    }

    public record PolicySummary(
            Integer mileagePolicyId,
            Integer academicYear,
            String semesterCode,
            Integer versionNo,
            BigDecimal points,
            BigDecimal maximumPoints,
            LocalDate validFrom,
            LocalDate validTo,
            String policyStatus
    ) {
        private static PolicySummary from(MileagePolicy policy) {
            return new PolicySummary(
                    policy.getMileagePolicyId(),
                    policy.getAcademicYear(),
                    policy.getSemesterCode(),
                    policy.getVersionNo(),
                    policy.getPoints(),
                    policy.getMaximumPoints(),
                    policy.getValidFrom(),
                    policy.getValidTo(),
                    policy.getPolicyStatus());
        }
    }
}
