package com.gnagnoohc.scms.domain.counsel.dto;

import java.time.Instant;

/**
 * 회기 목록·상세·생성·완료·취소 응답에 공통으로 쓴다. 신청 원문, 비공개 기록, 공개 결과,
 * 학생 연락처는 포함하지 않는다(consultation-domain-api.md 상담 회기 관리 절).
 */
public record CounselingSessionResponse(
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
        boolean assignmentActive,
        boolean canCreateFollowUp,
        boolean canComplete,
        boolean canCancel
) {
    public static CounselingSessionResponse from(CounselingSessionRow row, Integer counselorId, Instant now) {
        return new CounselingSessionResponse(
                row.sessionId(),
                row.assignmentId(),
                row.reservationId(),
                row.sessionNo(),
                row.studentId(),
                row.studentNumber(),
                row.studentName(),
                row.departmentName(),
                row.counselingTypeName(),
                row.startsAt(),
                row.endsAt(),
                row.attendanceStatus(),
                row.sessionStatus(),
                row.nextSessionAt(),
                row.cancellationReason(),
                row.assignmentActive(),
                row.canCreateFollowUp(counselorId),
                row.canComplete(counselorId, now),
                row.canCancel(counselorId, now)
        );
    }
}
