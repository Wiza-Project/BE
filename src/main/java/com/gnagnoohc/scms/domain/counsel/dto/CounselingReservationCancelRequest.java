package com.gnagnoohc.scms.domain.counsel.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 학생 본인이 예약을 취소할 때 반드시 사유를 남기게 한다.
 */
public record CounselingReservationCancelRequest(
        @NotBlank String cancellationReason
) {
}
