package com.gnagnoohc.scms.domain.program.dto.attendance;

import com.gnagnoohc.scms.domain.program.entity.ProgramAttendance;

import java.time.Instant;

// 비교과프로그램 출석 기록 응답 DTO. 출석 기록 응답과 회차별 출석 목록 조회 응답에 공통으로 사용한다.
public record ProgramAttendanceResponseDTO(
        Integer attendanceId,
        Integer applicationId,
        Integer programSessionId,
        String attendanceStatus,
        Integer attendedMinutes,
        String note,
        Integer recordedBy,
        Instant recordedAt
) {
    public static ProgramAttendanceResponseDTO from(ProgramAttendance attendance) {
        return new ProgramAttendanceResponseDTO(
                attendance.getAttendanceId(),
                attendance.getApplication().getApplicationId(),
                attendance.getProgramSession().getProgramSessionId(),
                attendance.getAttendanceStatus(),
                attendance.getAttendedMinutes(),
                attendance.getNote(),
                attendance.getRecordedBy(),
                attendance.getRecordedAt()
        );
    }
}
