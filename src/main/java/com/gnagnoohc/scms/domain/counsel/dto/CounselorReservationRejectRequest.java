package com.gnagnoohc.scms.domain.counsel.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 상담사가 예약을 거절할 때 학생에게 공개될 거절 사유를 반드시 남기게 한다.
 */
public record CounselorReservationRejectRequest(
        @NotBlank String decisionReason
) {
}
