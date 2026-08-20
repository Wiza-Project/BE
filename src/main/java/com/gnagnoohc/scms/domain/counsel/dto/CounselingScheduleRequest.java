package com.gnagnoohc.scms.domain.counsel.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 일정 등록과 전체 수정에 공통으로 사용하는 입력 계약이다.
 * 단일 필드 형식은 여기서 검사하고, 시간 순서와 같은 필드 간 규칙은 서비스에서 검사한다.
 */
public record CounselingScheduleRequest(
        @NotNull @Positive Integer counselingTypeId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @NotNull @Min(1) Integer capacity,
        Instant bookingDeadline,
        @Size(max = 300) String location
) {
}
