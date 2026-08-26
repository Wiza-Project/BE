package com.gnagnoohc.scms.domain.counsel.dto;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingAssignment;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;

import java.time.Instant;

/**
 * 승인 처리 결과다. 예약 상태와 함께, 승인과 동시에 생성된 최초 활성 배정 정보를 같이 보여준다.
 */
public record CounselorReservationDecisionResponse(
        Integer reservationId,
        String reservationStatus,
        Instant processedAt,
        Integer counselingAssignmentId,
        Integer counselorId,
        Instant assignedAt
) {
    public static CounselorReservationDecisionResponse from(
            CounselingReservation reservation,
            CounselingAssignment assignment
    ) {
        return new CounselorReservationDecisionResponse(
                reservation.getCounselingReservationId(),
                reservation.getReservationStatus(),
                reservation.getProcessedAt(),
                assignment.getCounselingAssignmentId(),
                assignment.getCounselor().getUserId(),
                assignment.getAssignedAt()
        );
    }
}
