package com.gnagnoohc.scms.domain.mileage.DTO.response;

import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.support.MileageJsonNodeConverter;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// 마일리지 정책 응답 DTO. 등록/목록/상세/수정 응답에 공통으로 사용한다.
public record MileagePolicyResponseDTO(
        Integer mileagePolicyId,
        Integer activityTypeId,
        String activityName,
        String semesterCode,
        Integer versionNo,
        BigDecimal points,
        BigDecimal maximumPoints,
        LocalDate validFrom,
        LocalDate validTo,
        JsonNode duplicateRule,
        String policyStatus,
        Instant createdAt,
        Integer createdBy
) {
    public static MileagePolicyResponseDTO from(MileagePolicy policy) {
        return new MileagePolicyResponseDTO(
                policy.getMileagePolicyId(),
                policy.getActivityType().getActivityTypeId(),
                policy.getActivityType().getActivityName(),
                policy.getSemesterCode(),
                policy.getVersionNo(),
                policy.getPoints(),
                policy.getMaximumPoints(),
                policy.getValidFrom(),
                policy.getValidTo(),
                MileageJsonNodeConverter.toJackson3(policy.getDuplicateRule()),
                policy.getPolicyStatus(),
                policy.getCreatedAt(),
                policy.getCreatedBy()
        );
    }
}
