package com.gnagnoohc.scms.domain.mileage.DTO.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

// 마일리지 활동별 점수 기준(정책) 등록 요청 DTO. version_no는 서버가 자동 채번하므로 요청값에 없다.
public record MileagePolicyRegisterRequestDTO(
        @NotNull Integer activityTypeId,
        @NotNull @Positive Integer academicYear,
        // 학기 코드. 생략(null/빈 문자열)하면 서비스에서 "ALL"(전학기 공통)로 채운다.
        @Size(max = 20) String semesterCode,
        @NotNull @DecimalMin("0") BigDecimal points,
        @DecimalMin("0") BigDecimal maximumPoints,
        @NotNull LocalDate validFrom,
        // 종료일 없음(null)은 "무기한 적용"을 의미한다.
        LocalDate validTo,
        // 중복 적립 규칙. 구조는 서버 계약으로 관리되며 여기서는 구조를 강제하지 않는다.
        JsonNode duplicateRule
) {
}
