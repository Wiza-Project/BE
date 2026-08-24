package com.gnagnoohc.scms.domain.counsel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 같은 상담 유형의 다른 OPEN DIRECT 일정으로 재배정할 때 새 일정과 변경 사유를 받는다.
 */
public record CounselingReservationScheduleChangeRequest(
        @NotNull @Positive Integer scheduleId,
        @NotBlank String changeReason
) {
}
