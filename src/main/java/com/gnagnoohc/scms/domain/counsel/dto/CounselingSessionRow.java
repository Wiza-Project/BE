package com.gnagnoohc.scms.domain.counsel.dto;

import java.time.Instant;

/**
 * 회기 목록·상세 JPQL 프로젝션 결과다. 엔티티/조인에서 오는 순수 데이터만 담고,
 * canCreateFollowUp/canComplete/canCancel 같은 요청자·시각 의존 판정은 여기서 계산하지 않는다
 * (같은 행이라도 어떤 상담사가 조회하느냐, 언제 조회하느냐에 따라 달라지기 때문).
 * {@link CounselingSessionResponse#from}이 요청자 counselorId와 now를 받아 계산한다.
 */
public record CounselingSessionRow(
        Integer sessionId,
        Integer assignmentId,
        Integer reservationId,
        Integer sessionNo,
        Integer studentId,
        String studentNumber,
        String studentName,
        String departmentName,
        String counselingTypeName,
        Instant startsAt,
        Instant endsAt,
        String attendanceStatus,
        String sessionStatus,
        Instant nextSessionAt,
        String cancellationReason,
        Instant assignedAt,
        Instant endedAt,
        Integer counselorId
) {
    private static final String SESSION_PLANNED = "PLANNED";

    public boolean assignmentActive() {
        return endedAt == null;
    }

    private boolean ownedBy(Integer requesterCounselorId) {
        return counselorId.equals(requesterCounselorId);
    }

    public boolean canCreateFollowUp(Integer requesterCounselorId) {
        return assignmentActive() && ownedBy(requesterCounselorId);
    }

    public boolean canComplete(Integer requesterCounselorId, Instant now) {
        return assignmentActive() && ownedBy(requesterCounselorId)
                && SESSION_PLANNED.equals(sessionStatus) && endsAt != null && now.isAfter(endsAt);
    }

    public boolean canCancel(Integer requesterCounselorId, Instant now) {
        return assignmentActive() && ownedBy(requesterCounselorId)
                && SESSION_PLANNED.equals(sessionStatus) && now.isBefore(startsAt);
    }
}
