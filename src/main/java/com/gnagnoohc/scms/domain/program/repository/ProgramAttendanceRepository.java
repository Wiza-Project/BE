package com.gnagnoohc.scms.domain.program.repository;

import com.gnagnoohc.scms.domain.program.entity.ProgramAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProgramAttendanceRepository extends JpaRepository<ProgramAttendance, Integer> {

    /**
     * ProgramAttendance 엔티티도 setter/빌더가 없어 native SQL로 우회한다.
     * application_id + program_session_id 조합에 uq_program_attendance_application_session
     * 유니크 제약이 걸려있어, 같은 학생·회차에 대한 재기록(정정)은 ON CONFLICT DO UPDATE로 처리한다
     * (INSERT와 UPDATE를 서비스 계층에서 미리 구분할 필요 없이 이 쿼리 하나로 upsert된다).
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
        INSERT INTO program_attendance (
            application_id, program_session_id, attendance_status, attended_minutes, note, recorded_by, recorded_at
        ) VALUES (
            :applicationId, :programSessionId, :attendanceStatus, :attendedMinutes, :note, :recordedBy, :now
        )
        ON CONFLICT (application_id, program_session_id)
        DO UPDATE SET
            attendance_status = EXCLUDED.attendance_status,
            attended_minutes = EXCLUDED.attended_minutes,
            note = EXCLUDED.note,
            recorded_by = EXCLUDED.recorded_by,
            recorded_at = EXCLUDED.recorded_at
        """, nativeQuery = true)
    void upsertAttendance(@Param("applicationId") Integer applicationId,
                           @Param("programSessionId") Integer programSessionId,
                           @Param("attendanceStatus") String attendanceStatus,
                           @Param("attendedMinutes") Integer attendedMinutes,
                           @Param("note") String note,
                           @Param("recordedBy") Integer recordedBy,
                           @Param("now") Instant now);

    List<ProgramAttendance> findByProgramSession_ProgramSessionId(Integer programSessionId);

    /**
     * 학생 본인의 출결 조회(ProgramAttendanceService.listMyAttendance)에서, 신청 건 하나에 기록된
     * 회차별 출결을 전부 가져와 회차 목록과 메모리에서 합칠 때 사용한다.
     */
    List<ProgramAttendance> findByApplication_ApplicationId(Integer applicationId);

    // upsertAttendance 실행 직후, 방금 upsert된 row를 다시 읽어 응답 DTO를 만드는 데 사용한다.
    Optional<ProgramAttendance> findByApplication_ApplicationIdAndProgramSession_ProgramSessionId(
            Integer applicationId, Integer programSessionId);

    /**
     * 신청 내역 목록 화면의 출석률 표시를 위해, 신청 건 여러 개의 출석 집계를 한 번에 조회한다(N+1 방지).
     */
    @Query("""
        SELECT a.application.applicationId AS applicationId,
               COUNT(a) AS totalCount,
               SUM(CASE WHEN a.attendanceStatus = 'PRESENT' THEN 1L ELSE 0L END) AS presentCount
        FROM ProgramAttendance a
        WHERE a.application.applicationId IN :applicationIds
        GROUP BY a.application.applicationId
        """)
    List<AttendanceCountProjection> countAttendanceByApplicationIds(@Param("applicationIds") List<Integer> applicationIds);

    /** 신청 건별 출석 집계 쿼리의 조회 전용 결과다. */
    interface AttendanceCountProjection {
        /** 신청 건 PK */
        Integer getApplicationId();
        /** 해당 신청 건에 기록된 전체 출결 건수 */
        Long getTotalCount();
        /** 그 중 출석(PRESENT)으로 기록된 건수 */
        Long getPresentCount();
    }
}
