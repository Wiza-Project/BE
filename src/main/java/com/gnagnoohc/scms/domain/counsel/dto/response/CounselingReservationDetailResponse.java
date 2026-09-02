package com.gnagnoohc.scms.domain.counsel.dto.response;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;

import java.time.Instant;

/**
 * 본인만 조회할 수 있는 예약 상세 정보다.
 */
public record CounselingReservationDetailResponse(
        Integer reservationId,
        Integer counselingTypeId,
        Integer counselingScheduleId,
        String reservationStatus,
        String requestContent,
        Instant createdAt
) {
    public static CounselingReservationDetailResponse from(CounselingReservation reservation) {
        Integer scheduleId = reservation.getCounselingSchedule() == null
                ? null
                : reservation.getCounselingSchedule().getCounselingScheduleId();
        return new CounselingReservationDetailResponse(
                reservation.getCounselingReservationId(),
                reservation.getCounselingType().getCounselingTypeId(),
                scheduleId,
                reservation.getReservationStatus(),
                reservation.getRequestContent(),
                reservation.getCreatedAt()
        );
    }
}
