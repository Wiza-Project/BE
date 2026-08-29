package com.gnagnoohc.scms.domain.mileage.DTO.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// 마일리지 활동 유형 등록 요청 DTO. activityTypeId와 createdBy는 서버가 관리한다.
public record MileageActivityTypeRegisterRequestDTO(
        @NotNull @Positive Integer competencyId,
        @NotBlank @Size(max = 40) String activityCode,
        @NotBlank @Size(max = 40) String categoryCode,
        @NotBlank @Size(max = 150) String activityName,
        @NotBlank @Size(max = 30) String earningRoute
) {
}
