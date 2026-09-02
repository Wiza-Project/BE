package com.gnagnoohc.scms.domain.counsel.dto.response;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingAssignment;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSession;

import java.time.Instant;

/**
 * 승인 처리 결과다. 예약 상태, 승인과 동시에 생성된 최초 활성 배정, 그리고 함께 생성된 1회기 정보를
 * 같이 보여준다(체크리스트 7번 확장: consultation-domain-api.md "예약 승인 시 1회기 자동 생성 확장").
 */
public record CounselorReservationDecisionResponse(
        Integer reservationId,
        String reservationStatus,
        Instant processedAt,
        Integer counselingAssignmentId,
        Integer counselorId,
        Instant assignedAt,
        Integer counselingSessionId,
        Integer sessionNo,
        Instant sessionStartsAt,
        Instant sessionEndsAt,
        String attendanceStatus,
        String sessionStatus
) {
    public static CounselorReservationDecisionResponse from(
            CounselingReservation reservation,
            CounselingAssignment assignment,
            CounselingSession session
    ) {
        return new CounselorReservationDecisionResponse(
                reservation.getCounselingReservationId(),
                reservation.getReservationStatus(),
                reservation.getProcessedAt(),
                assignment.getCounselingAssignmentId(),
                assignment.getCounselor().getUserId(),
                assignment.getAssignedAt(),
                session.getCounselingSessionId(),
                session.getSessionNo(),
                session.getStartsAt(),
                session.getEndsAt(),
                session.getAttendanceStatus(),
                session.getSessionStatus()
        );
    }
}
