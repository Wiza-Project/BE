package com.gnagnoohc.scms.domain.counsel.dto;

import java.time.Instant;

/**
 * 상담사가 본인 일정을 관리할 때 필요한 최소 정보만 반환한다.
 * hasReservation은 수정 가능 여부를 미리 표시하기 위한 값이며, 실제 수정 시에는 서비스가 다시 검증한다.
 * remainingCapacity는 대행 예약 등에서 참고할 잔여 정원(0 이상)이며 실제 생성 시에는 서비스가
 * 다시 잠그고 재검증하므로 여기 값을 그대로 신뢰하지 않는다.
 */
public record CounselorScheduleResponse(
        Integer scheduleId,
        Integer counselingTypeId,
        Integer counselorId,
        Instant startsAt,
        Instant endsAt,
        Integer capacity,
        Instant bookingDeadline,
        String location,
        String status,
        boolean hasReservation,
        Integer remainingCapacity
) {
}
