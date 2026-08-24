package com.gnagnoohc.scms.domain.mileage.DTO.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 마일리지 정책 수정 요청 DTO. 활동유형/학년도/학기/버전(식별 필드)은 변경할 수 없고,
 * 그 외 필드는 부분 수정을 허용한다 — 각 필드가 null이면 기존 값을 그대로 유지한다.
 */
public record MileagePolicyUpdateRequestDTO(
        @DecimalMin("0") BigDecimal points,
        @DecimalMin("0") BigDecimal maximumPoints,
        LocalDate validFrom,
        LocalDate validTo,
        JsonNode duplicateRule,
        @Pattern(regexp = "ACTIVE|INACTIVE|EXPIRED", message = "정책 상태는 ACTIVE, INACTIVE, EXPIRED 중 하나여야 합니다.")
        String policyStatus
) {
}
