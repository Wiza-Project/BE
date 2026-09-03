package com.gnagnoohc.scms.domain.counsel.dto.response;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;

import java.time.Instant;

/**
 * 담당 상담사만 볼 수 있는 예약 상세 정보다.
 * 목록(CounselorPendingReservationResponse)과 달리 requestContent(신청 내용)를 포함한다.
 */
public record CounselorReservationDetailResponse(
        Integer reservationId,
        Integer counselingTypeId,
        String counselingTypeName,
        Integer studentId,
        Integer counselingScheduleId,
        Instant startsAt,
        Instant endsAt,
        String reservationStatus,
        String requestContent,
        Integer processedBy,
        Instant processedAt,
        String decisionReason,
        Instant createdAt
) {
    public static CounselorReservationDetailResponse from(CounselingReservation reservation) {
        Integer scheduleId = reservation.getCounselingSchedule() == null
                ? null
                : reservation.getCounselingSchedule().getCounselingScheduleId();
        Instant startsAt = reservation.getCounselingSchedule() == null
                ? null
                : reservation.getCounselingSchedule().getStartsAt();
        Instant endsAt = reservation.getCounselingSchedule() == null
                ? null
                : reservation.getCounselingSchedule().getEndsAt();
        return new CounselorReservationDetailResponse(
                reservation.getCounselingReservationId(),
                reservation.getCounselingType().getCounselingTypeId(),
                reservation.getCounselingType().getTypeName(),
                reservation.getStudent().getUserId(),
                scheduleId,
                startsAt,
                endsAt,
                reservation.getReservationStatus(),
                reservation.getRequestContent(),
                reservation.getProcessedBy(),
                reservation.getProcessedAt(),
                reservation.getDecisionReason(),
                reservation.getCreatedAt()
        );
    }
}
