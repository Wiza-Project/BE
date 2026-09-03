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
        Instant applicationEndsAt,
        // 배타적 단계(tier) 그룹 식별자. 같은 값을 지정한 정책끼리는 학생이 그중 하나만 신청할 수 있다. 생략하면 배타 그룹 없음.
        @Size(max = 50) String benefitGroupCode,
        // 자격 판정 시 합산할 연속 연도 수. 생략하면 1(단일 학년도 합산)로 처리된다. 2 이상이면 여러 학년도 누적 합산("4년누적" 등).
        @Positive Integer cumulativeYears,
        // true면 minimumPoints "이상"이 아니라 정확히 일치해야 자격을 충족한다. 생략하면 false.
        boolean requiresExactPoints
) {
}
