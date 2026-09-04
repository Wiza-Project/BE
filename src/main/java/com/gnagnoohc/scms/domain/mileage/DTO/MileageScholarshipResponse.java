package com.gnagnoohc.scms.domain.mileage.DTO;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;

/** 학생 장학금 조회·신청·신청 이력 화면에서 사용하는 응답 모델. */
public final class MileageScholarshipResponse {

    private MileageScholarshipResponse() {
    }

    /** 장학금 목록과 상세 화면에 공통으로 사용하는 정책 진행도다. */
    public record ScholarshipItem(
            Integer benefitPolicyId,
            String benefitType,
            String benefitName,
            String semesterCode,
            BigDecimal minimumPoints,
            BigDecimal currentPoints,
            BigDecimal shortagePoints,
            BigDecimal benefitAmount,
            JsonNode criteriaData,
            Instant applicationStartsAt,
            Instant applicationEndsAt,
            String eligibilityStatus,
            String applicationStatus,
            boolean canApply,
            Integer cumulativeYears,
            String benefitGroupCode
    ) {
    }

    /** 학생 본인의 장학금 신청 이력 한 건이다. */
    public record ApplicationItem(
            Integer benefitApplicationId,
            Integer benefitPolicyId,
            String benefitName,
            String semesterCode,
            BigDecimal benefitAmount,
            BigDecimal pointsSnapshot,
            String applicationStatus,
            Instant appliedAt,
            Instant processedAt,
            String decisionReason
    ) {
    }
}
