package com.gnagnoohc.scms.domain.competency.dto.response;

/**
 * 미응시자 목록(GET /api/staff/assessment-rounds/{roundId}/non-participants) 한 행.
 * 개인정보(학번·이름·연락처)를 포함하므로 응시율 조회(AssessmentAttendanceResponse)와
 * 별도 DTO로 둔다.
 */
public record AssessmentNonParticipantResponse(
        Integer userId,
        String studentId,
        String name,
        String email,
        String phone,
        String majorName,
        Integer grade
) {}
