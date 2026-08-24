package com.gnagnoohc.scms.domain.counsel.dto;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;

import java.time.Instant;

/**
 * 상담사의 승인 대기(REQUESTED) 예약 목록에 쓰는 최소 정보다.
 * requestContent(신청 내용)는 민감할 수 있으므로 목록에는 포함하지 않고 상세 조회에서만 보여준다.
 */
public record CounselorPendingReservationResponse(
        Integer reservationId,
        Integer counselingTypeId,
        String counselingTypeName,
        Integer studentId,
        Integer counselingScheduleId,
        Instant startsAt,
        Instant endsAt,
        String reservationStatus,
        Instant createdAt
) {
    public static CounselorPendingReservationResponse from(CounselingReservation reservation) {
        return new CounselorPendingReservationResponse(
                reservation.getCounselingReservationId(),
                reservation.getCounselingType().getCounselingTypeId(),
                reservation.getCounselingType().getTypeName(),
                reservation.getStudent().getUserId(),
                reservation.getCounselingSchedule().getCounselingScheduleId(),
                reservation.getCounselingSchedule().getStartsAt(),
                reservation.getCounselingSchedule().getEndsAt(),
                reservation.getReservationStatus(),
                reservation.getCreatedAt()
        );
    }
}
