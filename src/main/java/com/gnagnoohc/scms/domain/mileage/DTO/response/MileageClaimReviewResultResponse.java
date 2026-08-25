package com.gnagnoohc.scms.domain.mileage.DTO.response;

import com.gnagnoohc.scms.domain.mileage.entity.ExternalActivityClaim;
import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;

import java.math.BigDecimal;
import java.time.Instant;

/** 외부활동 마일리지 신청 승인·반려 처리 결과다. */
public record MileageClaimReviewResultResponse(
        Integer externalClaimId,
        String claimStatus,
        BigDecimal postedPoints,
        Integer mileageTransactionId,
        Integer reviewedBy,
        Instant reviewedAt,
        String reviewReason
) {

    public static MileageClaimReviewResultResponse from(
            ExternalActivityClaim claim,
            MileageTransaction transaction
    ) {
        return new MileageClaimReviewResultResponse(
                claim.getExternalClaimId(),
                claim.getClaimStatus(),
                transaction == null ? null : transaction.getPoints(),
                transaction == null ? null : transaction.getMileageTransactionId(),
                claim.getReviewedBy(),
                claim.getReviewedAt(),
                claim.getReviewReason());
    }
}
