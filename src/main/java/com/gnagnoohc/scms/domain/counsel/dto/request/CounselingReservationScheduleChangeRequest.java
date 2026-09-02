package com.gnagnoohc.scms.domain.counsel.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 학생이 자기 예약의 일정을 같은 상담 유형의 다른 OPEN DIRECT 일정으로 변경할 때 쓰는 요청이다.
 * expectedScheduleId는 학생 화면이 모달을 열 때 조회해 둔 "현재" 예약 일정 ID로, 예약 행을 잠근
 * 뒤 DB의 실제 현재 일정과 비교해 그 사이에 다른 요청이 먼저 일정을 바꿨는지(stale) 판별하는 데 쓴다.
 * scheduleId는 새로 옮겨갈 일정이며 expectedScheduleId와 달라야 한다(같으면 무변경 요청으로 거절).
 */
public record CounselingReservationScheduleChangeRequest(
        @NotNull @Positive Integer expectedScheduleId,
        @NotNull @Positive Integer scheduleId,
        @NotBlank String changeReason
) {
}
