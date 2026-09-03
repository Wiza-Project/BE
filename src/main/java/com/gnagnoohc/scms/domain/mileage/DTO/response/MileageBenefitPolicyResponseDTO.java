package com.gnagnoohc.scms.domain.mileage.DTO.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy;

import java.math.BigDecimal;
import java.time.Instant;

// 마일리지 혜택 정책 응답 DTO. 등록/목록/상세/수정 응답에 공통으로 사용한다.
public record MileageBenefitPolicyResponseDTO(
        Integer benefitPolicyId,
        String benefitType,
        Integer academicYear,
        String semesterCode,
        String benefitName,
        BigDecimal minimumPoints,
        BigDecimal benefitAmount,
        JsonNode criteriaData,
        Instant applicationStartsAt,
        Instant applicationEndsAt,
        boolean active,
        Instant createdAt,
        Integer createdBy,
        String benefitGroupCode,
        Integer cumulativeYears,
        boolean requiresExactPoints
) {
    public static MileageBenefitPolicyResponseDTO from(MileageBenefitPolicy policy) {
        return new MileageBenefitPolicyResponseDTO(
                policy.getBenefitPolicyId(),
                policy.getBenefitType(),
                policy.getAcademicYear(),
                policy.getSemesterCode(),
                policy.getBenefitName(),
                policy.getMinimumPoints(),
                policy.getBenefitAmount(),
                policy.getCriteriaData(),
                policy.getApplicationStartsAt(),
                policy.getApplicationEndsAt(),
                policy.isActive(),
                policy.getCreatedAt(),
                policy.getCreatedBy(),
                policy.getBenefitGroupCode(),
                policy.getCumulativeYears(),
                policy.isRequiresExactPoints()
        );
    }
}
