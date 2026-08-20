package com.gnagnoohc.scms.domain.program.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

// 비교과프로그램 등록 요청 DTO. 클라이언트가 보내는 입력값만 담는다.
// managerUserId(등록 담당자)와 programStatus(등록 시 DRAFT 고정)는 서버가 결정하므로
// 클라이언트가 위조할 수 없게 요청 필드에서 의도적으로 뺐다.

public record ProgramRegisterRequestDTO(
        Integer fileGroupId,
        @NotNull Integer operatingUnitCodeId,
        @NotNull Integer programTypeCodeId,
        @NotNull Integer competencyId,
        Integer mileagePolicyId,
        @NotBlank @Size(max = 200) String programName,
        String description,
        @NotNull Instant recruitmentStartsAt,
        @NotNull Instant recruitmentEndsAt,
        @NotNull Instant operationStartsAt,
        @NotNull Instant operationEndsAt,
        @NotNull @Positive Integer capacity,
        @DecimalMin("0") @DecimalMax("100") BigDecimal completionRate
) {
}
