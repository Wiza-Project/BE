package com.gnagnoohc.scms.domain.counsel.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 학생이 직접 선택한 유형, 일정과 선택 동의 이력만 받는다.
 * 학생 ID와 예약 상태는 인증 주체와 서버 규칙으로 결정하므로 요청으로 받지 않는다.
 */
public record CounselingReservationRequest(
        @NotNull @Positive Integer counselingTypeId,
        @Positive Integer scheduleId,
        @NotNull @Positive Integer consentId,
        @NotBlank String requestContent
) {
}
