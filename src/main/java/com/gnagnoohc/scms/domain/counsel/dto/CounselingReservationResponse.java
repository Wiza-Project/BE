package com.gnagnoohc.scms.domain.counsel.dto;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;

import java.time.Instant;

/**
 * 목록과 생성 결과에 쓰는 최소 예약 정보다.
 * 신청 내용은 민감할 수 있으므로 목록에는 포함하지 않는다.
 */
public record CounselingReservationResponse(
        Integer reservationId,
        Integer counselingTypeId,
        Integer counselingScheduleId,
        String reservationStatus,
        Instant createdAt
) {
    public static CounselingReservationResponse from(CounselingReservation reservation) {
        Integer scheduleId = reservation.getCounselingSchedule() == null
                ? null
                : reservation.getCounselingSchedule().getCounselingScheduleId();
        return new CounselingReservationResponse(
                reservation.getCounselingReservationId(),
                reservation.getCounselingType().getCounselingTypeId(),
                scheduleId,
                reservation.getReservationStatus(),
                reservation.getCreatedAt()
        );
    }
}
