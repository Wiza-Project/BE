package com.gnagnoohc.scms.domain.counsel.dto.response;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSchedule;

import java.time.Instant;

/**
 * 목록과 생성 결과에 쓰는 최소 예약 정보다.
 * 신청 내용은 민감할 수 있으므로 목록에는 포함하지 않는다.
 * startsAt/endsAt은 연결된 일정의 시작·종료 시각(UTC Instant)이며, 일정이 없는
 * 레거시 CENTER 예약(counselingScheduleId == null)에서만 함께 null이 된다.
 */
public record CounselingReservationResponse(
        Integer reservationId,
        Integer counselingTypeId,
        Integer counselingScheduleId,
        Instant startsAt,
        Instant endsAt,
        String reservationStatus,
        Instant createdAt
) {
    public static CounselingReservationResponse from(CounselingReservation reservation) {
        CounselingSchedule schedule = reservation.getCounselingSchedule();
        return new CounselingReservationResponse(
                reservation.getCounselingReservationId(),
                reservation.getCounselingType().getCounselingTypeId(),
                schedule == null ? null : schedule.getCounselingScheduleId(),
                schedule == null ? null : schedule.getStartsAt(),
                schedule == null ? null : schedule.getEndsAt(),
                reservation.getReservationStatus(),
                reservation.getCreatedAt()
        );
    }
}
