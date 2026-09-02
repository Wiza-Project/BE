package com.gnagnoohc.scms.domain.mileage.DTO.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 마일리지 혜택 정책 수정 요청 DTO. 혜택유형/학년도/학기(식별 필드)는 변경할 수 없고,
 * 그 외 필드는 부분 수정을 허용한다 — 각 필드가 null이면 기존 값을 그대로 유지한다.
 */
public record MileageBenefitPolicyUpdateRequestDTO(
        @Size(max = 150) String benefitName,
        @DecimalMin("0") BigDecimal minimumPoints,
        @DecimalMin("0") BigDecimal benefitAmount,
        JsonNode criteriaData,
        Instant applicationStartsAt,
        Instant applicationEndsAt,
        Boolean active
) {
}
