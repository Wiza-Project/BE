package com.gnagnoohc.scms.domain.counsel.dto.response;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingSchedule;

import java.time.Instant;

/**
 * 일정 관리 결과에 필요한 값만 외부에 공개하는 응답 DTO다.
 * 사용자 계정이나 상담 유형 엔티티 전체를 반환하지 않아 내부 정보가 함께 노출되는 것을 막는다.
 */
public record CounselingScheduleResponse(
        Integer scheduleId,
        Integer counselingTypeId,
        Integer counselorId,
        Instant startsAt,
        Instant endsAt,
        Integer capacity,
        Instant bookingDeadline,
        String location,
        String status
) {
    /**
     * 트랜잭션 안에서 엔티티의 식별자와 일정 정보만 골라 응답으로 바꾼다.
     */
    public static CounselingScheduleResponse from(CounselingSchedule schedule) {
        return new CounselingScheduleResponse(
                schedule.getCounselingScheduleId(),
                schedule.getCounselingType().getCounselingTypeId(),
                schedule.getCounselor().getUserId(),
                schedule.getStartsAt(),
                schedule.getEndsAt(),
                schedule.getCapacity(),
                schedule.getBookingDeadline(),
                schedule.getLocation(),
                schedule.getScheduleStatus()
        );
    }
}
