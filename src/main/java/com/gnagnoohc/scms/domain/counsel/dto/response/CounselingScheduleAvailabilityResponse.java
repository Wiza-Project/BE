package com.gnagnoohc.scms.domain.counsel.dto.response;

import java.time.Instant;

/**
 * 학생의 일정 선택에 필요한 정보만 제공하고 상담사의 연락처 등 불필요한 개인정보는 제외한다.
 */
public record CounselingScheduleAvailabilityResponse(
        Integer scheduleId,
        String counselorName,
        String counselorDepartmentName,
        Instant startsAt,
        Instant endsAt,
        Instant bookingDeadline,
        String location,
        Integer remainingCapacity
) {
    /**
     * 조회 쿼리의 전체 정원과 점유 예약 수를 학생에게 보여줄 잔여 정원으로 변환한다.
     */
    public CounselingScheduleAvailabilityResponse(
            Integer scheduleId,
            String counselorName,
            String counselorDepartmentName,
            Instant startsAt,
            Instant endsAt,
            Instant bookingDeadline,
            String location,
            Integer capacity,
            Long occupiedReservationCount
    ) {
        this(
                scheduleId,
                counselorName,
                counselorDepartmentName,
                startsAt,
                endsAt,
                bookingDeadline,
                location,
                capacity - occupiedReservationCount.intValue()
        );
    }
}
