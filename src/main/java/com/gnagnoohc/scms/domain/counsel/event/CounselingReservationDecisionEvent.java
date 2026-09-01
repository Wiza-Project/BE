package com.gnagnoohc.scms.domain.counsel.event;

import java.time.Instant;

/**
 * 기존 상담사 승인, 상담사 대행 예약 성공, 상담사 반려가 공유하는 커밋 후 알림 이벤트다.
 * 알림 생성에 필요한 최소값만 담고, 신청 내용·학번·학생 이름 등 민감정보는 담지 않는다.
 */
public record CounselingReservationDecisionEvent(
        Integer reservationId,
        Integer studentId,
        String decisionStatus,
        Instant sessionStartsAt,
        Instant sessionEndsAt,
        String location,
        String decisionReason
) {
    public static final String CONFIRMED = "CONFIRMED";
    public static final String REJECTED = "REJECTED";

    /** 기존 상담사 승인과 상담사 대행 예약 성공이 공통으로 사용하는 확정 이벤트다. */
    public static CounselingReservationDecisionEvent confirmed(
            Integer reservationId,
            Integer studentId,
            Instant sessionStartsAt,
            Instant sessionEndsAt,
            String location
    ) {
        return new CounselingReservationDecisionEvent(
                reservationId, studentId, CONFIRMED, sessionStartsAt, sessionEndsAt, location, null
        );
    }

    /** 상담사 반려에서 사용하는 이벤트다. 일정 정보는 알림 문구에 필요하지 않아 담지 않는다. */
    public static CounselingReservationDecisionEvent rejected(
            Integer reservationId,
            Integer studentId,
            String decisionReason
    ) {
        return new CounselingReservationDecisionEvent(
                reservationId, studentId, REJECTED, null, null, null, decisionReason
        );
    }
}
