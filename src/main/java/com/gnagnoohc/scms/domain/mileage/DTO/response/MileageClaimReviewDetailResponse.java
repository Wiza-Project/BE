package com.gnagnoohc.scms.domain.mileage.DTO.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.mileage.entity.ExternalActivityClaim;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 교직원이 학생·증빙·정책·처리 이력을 함께 확인하는 상세 응답이다. */
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
        TransactionSummary originalTransaction,
        TransactionSummary reversalTransaction
) {

    public static MileageClaimReviewDetailResponse from(
            ExternalActivityClaim claim,
            MileageTransaction originalTransaction,
            MileageTransaction reversalTransaction
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
                TransactionSummary.from(originalTransaction),
                TransactionSummary.from(reversalTransaction)
        );
    }

    public record StudentSummary(Integer studentId, String studentName, String studentNo) {
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
                    policy.getSemesterCode(),
                    policy.getVersionNo(),
                    policy.getPoints(),
                    policy.getMaximumPoints(),
                    policy.getValidFrom(),
                    policy.getValidTo(),
                    policy.getPolicyStatus());
        }
    }

    public record TransactionSummary(
            Integer transactionId,
            String transactionType,
            BigDecimal points,
            String transactionStatus,
            Integer processedBy,
            String transactionReason,
            Instant postedAt
    ) {
        private static TransactionSummary from(MileageTransaction transaction) {
            if (transaction == null) {
                return null;
            }
            return new TransactionSummary(
                    transaction.getMileageTransactionId(),
                    transaction.getTransactionType(),
                    transaction.getPoints(),
                    transaction.getTransactionStatus(),
                    transaction.getProcessedBy(),
                    transaction.getTransactionReason(),
                    transaction.getPostedAt());
        }
    }
}
