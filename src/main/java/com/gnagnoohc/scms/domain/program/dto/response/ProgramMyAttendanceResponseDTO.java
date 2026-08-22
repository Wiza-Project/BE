package com.gnagnoohc.scms.domain.program.dto.response;

import com.gnagnoohc.scms.domain.program.entity.ProgramAttendance;
import com.gnagnoohc.scms.domain.program.entity.ProgramSession;

import java.time.Instant;

/**
 * 학생 본인의 회차별 출결 조회 응답 DTO. ProgramAttendanceResponseDTO와 달리 회차 정보(sessionNo 등)를
 * 포함하고, 아직 출결이 기록되지 않은 회차는 attendance 관련 필드가 전부 null로 내려간다
 * (해당 회차에 출결 기록 자체가 없다는 뜻 — "미확인"을 이렇게 표현한다).
 */
public record ProgramMyAttendanceResponseDTO(
        Integer programSessionId,
        Integer sessionNo,
        String sessionName,
        Instant startsAt,
        Instant endsAt,
        String location,
        String attendanceStatus,
        Integer attendedMinutes,
        String note
) {
    public static ProgramMyAttendanceResponseDTO of(ProgramSession session, ProgramAttendance attendance) {
        return new ProgramMyAttendanceResponseDTO(
                session.getProgramSessionId(),
                session.getSessionNo(),
                session.getSessionName(),
                session.getStartsAt(),
                session.getEndsAt(),
                session.getLocation(),
                attendance != null ? attendance.getAttendanceStatus() : null,
                attendance != null ? attendance.getAttendedMinutes() : null,
                attendance != null ? attendance.getNote() : null
        );
    }
}
