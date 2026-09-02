package com.gnagnoohc.scms.domain.mileage.DTO.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

// 인증·장학 등 마일리지 혜택 정책 등록 요청 DTO.
public record MileageBenefitPolicyRegisterRequestDTO(
        @NotBlank @Size(max = 20) String benefitType,
        @NotNull @Positive Integer academicYear,
        // 학기 코드. 생략(null/빈 문자열)하면 서비스에서 "ALL"(연간 공통)로 채운다.
        @Size(max = 20) String semesterCode,
        @NotBlank @Size(max = 150) String benefitName,
        @NotNull @DecimalMin("0") BigDecimal minimumPoints,
        @DecimalMin("0") BigDecimal benefitAmount,
        // 판정 기준의 부가 정보. 구조는 서버 계약으로 관리되며 여기서는 구조를 강제하지 않는다.
        JsonNode criteriaData,
        Instant applicationStartsAt,
        Instant applicationEndsAt
) {
}
