package com.gnagnoohc.scms.domain.program.repository;

import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProgramApplicationRepository extends JpaRepository<ProgramApplication, Integer> {

    /**
     * ── 여기부터 "신청자 수 집계" 기능 (목록/상세 조회에서 공통으로 사용) ──────────────────
     *
     * 정원을 차지하는 상태는 APPLIED(정원 내 신청)/APPROVED(승인)뿐이다.
     * WAITLISTED는 대기이므로 정원 외, REJECTED/CANCELLED는 종결된 상태라 제외한다.
     */

    /**
     * 상세 조회 등 단일 프로그램의 신청자 수가 필요할 때 사용.
     */
    long countByProgram_ProgramIdAndApplicationStatusIn(Integer programId, List<String> statuses);

    /**
     * 목록 조회처럼 여러 프로그램의 신청자 수가 한 번에 필요할 때, 프로그램마다 개별 조회하면
     * N+1이 발생하므로 programId 목록을 받아 한 번의 GROUP BY 쿼리로 집계한다.
     */
    @Query("""
        SELECT a.program.programId AS programId, COUNT(a) AS count
        FROM ProgramApplication a
        WHERE a.program.programId IN :programIds
          AND a.applicationStatus IN ('APPLIED', 'APPROVED')
        GROUP BY a.program.programId
        """)
    List<ProgramApplicantCount> countActiveApplicantsByProgramIds(@Param("programIds") List<Integer> programIds);

    interface ProgramApplicantCount {
        Integer getProgramId();
        Long getCount();
    }

    /**
     * QR 자기체크인(ProgramAttendanceService.checkInWithQr)에서, "이 학생이 이 프로그램에 낸 신청 건"을
     * applicationId 없이 (programId, 로그인한 학생 id)만으로 찾을 때 사용한다.
     * program_id+student_id 조합은 uq_program_application_program_student 유니크 제약이 걸려있어 항상 0건 또는 1건이다.
     */
    Optional<ProgramApplication> findByProgram_ProgramIdAndStudent_UserId(Integer programId, Integer studentId);

    /**
     * 신청 건 row에 비관적 락을 걸어 조회한다. 승인/반려 처리 중 같은 신청 건이 동시에
     * 이중 처리되는 경쟁 조건을 막기 위해 사용한다 (ExtracurricularProgramRepository.findByIdForUpdate와 동일 패턴).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ProgramApplication a WHERE a.applicationId = :applicationId")
    Optional<ProgramApplication> findByIdForUpdate(@Param("applicationId") Integer applicationId);

    /**
     * ── 여기부터 "내 신청 현황 조회(Read)" 기능 ──────────────────────────────────────
     *
     * 학생이 자신의 전체 신청 내역을 최신순으로 조회한다. program은 지연 로딩(LAZY)이고
     * 응답에 프로그램명이 필요하므로 JOIN FETCH로 N+1을 방지한다. *-to-one 관계라
     * Pageable과 함께 써도 컬렉션 페치조인처럼 메모리 페이징 경고가 발생하지 않는다.
     */
    @Query(value = """
        SELECT a FROM ProgramApplication a
        JOIN FETCH a.program
        WHERE a.student.userId = :studentId
        """,
        countQuery = """
        SELECT COUNT(a) FROM ProgramApplication a
        WHERE a.student.userId = :studentId
        """)
    Page<ProgramApplication> findAllByStudentId(@Param("studentId") Integer studentId, Pageable pageable);

    /**
     * listMyApplications가 페이지 하나를 조회한 뒤, 그 신청 건들의 출석률을 한 번에 배치 조회할 때 쓴다.
     * judgeCompletion(...)의 CTE에 있는 것과 같은 출석률 계산식(PRESENT 출석 수 / 전체 회차 수 * 100)을
     * UPDATE가 아닌 단순 SELECT로 재사용하되, 상태 필터 없이 넘겨받은 applicationId 전체에 대해 계산한다
     * (신청 상태/이수 판정 여부와 무관하게 신청 내역 화면에는 출석률을 항상 보여줘야 하므로).
     * 회차가 하나도 없는 프로그램은 NULLIF(...,0)로 인해 rate가 null이 된다.
     */
    @Query(value = """
        SELECT pa.application_id AS applicationId,
               (SELECT COUNT(*) FROM program_attendance att
                JOIN program_session ps ON ps.program_session_id = att.program_session_id
                WHERE att.application_id = pa.application_id
                  AND att.attendance_status = 'PRESENT')::numeric
             / NULLIF((SELECT COUNT(*) FROM program_session ps2 WHERE ps2.program_id = pa.program_id), 0) * 100
             AS rate
        FROM program_application pa
        WHERE pa.application_id IN :applicationIds
        """, nativeQuery = true)
    List<ApplicationAttendanceRate> findAttendanceRatesByApplicationIds(
            @Param("applicationIds") List<Integer> applicationIds);

    interface ApplicationAttendanceRate {
        Integer getApplicationId();
        BigDecimal getRate();
    }

    /**
     * ── 여기부터 "참여 신청 접수(Create)" 기능 ──────────────────────────────────────
     *
     * ProgramApplication 엔티티는 ExtracurricularProgram과 같은 이유로 protected 기본 생성자만 있고
     * 빌더/setter가 없어서, 애플리케이션 코드에서 인스턴스를 만들어 save()로 저장할 방법이 없다.
     * 그래서 native INSERT로 우회한다 (ExtracurricularProgramRepository.insertProgram 참고).
     */
    @Query(value = """
        INSERT INTO program_application (
            program_id, student_id, application_status, waitlist_order, created_at, updated_at
        ) VALUES (
            :programId, :studentId, :applicationStatus, :waitlistOrder, :now, :now
        )
        RETURNING application_id
        """, nativeQuery = true)
    /**
     * RETURNING으로 새로 생성된 application_id를 그대로 돌려받아야 하므로 @Modifying을 붙이지 않는다.
     * program_id + student_id 조합은 uq_program_application_program_student 유니크 제약이 걸려있어,
     * 이미 신청한 학생이 다시 신청하면 이 INSERT가 DataIntegrityViolationException을 던진다
     * (서비스 계층에서 ErrorCode.ALREADY_APPLIED로 변환).
     */
    Integer insertApplication(@Param("programId") Integer programId,
                               @Param("studentId") Integer studentId,
                               /** "APPLIED" 또는 "WAITLISTED". 정원 비교 결과에 따라 서비스 계층이 결정한다. */
                               @Param("applicationStatus") String applicationStatus,
                               /** 정원 내 신청이면 null, 대기 신청이면 1부터 매겨지는 순번. */
                               @Param("waitlistOrder") Integer waitlistOrder,
                               @Param("now") Instant now);

    /**
     * 특정 프로그램에서 특정 상태(주로 "APPLIED")인 신청 건수. 정원과 비교해 대기 여부를 판단하는 데 쓴다.
     */
    long countByProgram_ProgramIdAndApplicationStatus(Integer programId, String applicationStatus);

    /**
     * 특정 프로그램의 현재까지 부여된 최대 대기순번. 대기 신청이 하나도 없으면 0을 반환한다
     * (다음 대기순번 = 이 값 + 1).
     */
    @Query("""
        SELECT COALESCE(MAX(a.waitlistOrder), 0)
        FROM ProgramApplication a
        WHERE a.program.programId = :programId
          AND a.applicationStatus = 'WAITLISTED'
        """)
    Integer findMaxWaitlistOrderByProgramId(@Param("programId") Integer programId);

    /**
     * ── 여기부터 "승인/반려 처리(Update)" 기능 ──────────────────────────────────────
     *
     * ProgramApplication 엔티티는 setter/빌더가 없어(insertApplication과 같은 이유),
     * 승인/반려 상태 변경도 네이티브 UPDATE로 처리한다 (ExtracurricularProgramRepository.updateProgram 참고).
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE program_application
        SET application_status = :applicationStatus,
            decision_reason = :decisionReason,
            processed_by = :processedBy,
            processed_at = :now,
            updated_at = :now
        WHERE application_id = :applicationId
        """, nativeQuery = true)
    int updateDecision(@Param("applicationId") Integer applicationId,
                        /** "APPROVED" 또는 "REJECTED". */
                        @Param("applicationStatus") String applicationStatus,
                        /** 반려 사유. 승인이면 null. */
                        @Param("decisionReason") String decisionReason,
                        /**
                         * 처리한 운영부서 담당자의 user_id. 클라이언트가 보낸 값이 아니라
                         * 서비스 계층에서 "지금 로그인한 사용자"의 id를 그대로 전달한다.
                         */
                        @Param("processedBy") Integer processedBy,
                        @Param("now") Instant now);

    /**
     * ── 여기부터 "출석 기반 이수 판정" 기능 ──────────────────────────────────────
     *
     * ExtracurricularProgramRepository.transitionOperatingToClosed와 같은 스타일의 벌크 native UPDATE.
     * 프로그램이 종료(CLOSED)된 뒤, 승인(APPROVED)된 신청 건마다 "출석한 회차 수 / 전체 회차 수"를
     * 계산해 프로그램의 이수 기준 출석률(completion_rate)과 비교한 결과를 completion_status에 채운다.
     *
     * WHERE 절의 "pa.completion_status IS NULL" 조건 덕분에 이 쿼리는 멱등하다: 매분 실행돼도
     * 이미 판정된 건은 다시 건드리지 않고, 방금 CLOSED로 전환된 프로그램의 아직 판정되지 않은
     * 승인 건만 새로 판정한다 (ProgramStatusScheduler의 시각 비교 멱등성과 같은 원리).
     * 회차가 하나도 없는 프로그램은 NULLIF(...,0)이 NULL이 되어 비교식이 거짓으로 평가되므로 FAILED로 처리된다.
     *
     * COMPLETED로 판정되는 순간, 이수번호(certificate_no)도 함께 채번한다. "수료증 발급"이라고 부르지만
     * 실제로 PDF 등을 만들어 어딘가 저장하는 게 아니라, 이수완료 시 서버가 고유한 이수번호 문자열을
     * 자동으로 부여해두는 것뿐이다(형식: CERT-연도-programId-applicationId — applicationId 자체가
     * PK라 이 조합은 항상 유일하다). FAILED로 판정되면 채번하지 않는다(NULL 유지).
     *
     * attendanceRate 계산을 completion_status/certificate_no 판정에서 반복하지 않도록 한 번만 계산해 재사용하고
     * 싶었는데, Postgres의 UPDATE ... FROM 에서는 LATERAL 서브쿼리가 UPDATE 대상 테이블(pa)을 참조할 수 없다
     * ("invalid reference to FROM-clause entry for table pa") — LATERAL은 FROM 목록의 다른 항목(ep 등)만
     * 참조 가능하고, UPDATE 대상 row는 그 목록에 속하지 않기 때문이다. 그래서 attendance_rate를 별도 CTE(WITH)로
     * 먼저 전부 계산해두고, UPDATE에서는 그 결과를 application_id로 평범하게(LATERAL 아닌 일반 조인 조건으로) 붙인다.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
        WITH attendance_rate AS (
            SELECT pa2.application_id,
                   (SELECT COUNT(*) FROM program_attendance att
                    JOIN program_session ps ON ps.program_session_id = att.program_session_id
                    WHERE att.application_id = pa2.application_id
                      AND att.attendance_status = 'PRESENT')::numeric
                 / NULLIF((SELECT COUNT(*) FROM program_session ps2 WHERE ps2.program_id = pa2.program_id), 0) * 100
                 AS rate
            FROM program_application pa2
            WHERE pa2.application_status = 'APPROVED'
              AND pa2.completion_status IS NULL
        )
        UPDATE program_application pa
        SET completion_status = CASE WHEN calc.rate >= ep.completion_rate THEN 'COMPLETED' ELSE 'FAILED' END,
            certificate_no = CASE WHEN calc.rate >= ep.completion_rate
                THEN 'CERT-' || to_char(CAST(:now AS timestamp), 'YYYY') || '-' || pa.program_id || '-' || pa.application_id
                ELSE NULL END,
            certificate_issued_at = CASE WHEN calc.rate >= ep.completion_rate THEN CAST(:now AS timestamptz) ELSE NULL END,
            completed_at = :now,
            updated_at = :now
        FROM extracurricular_program ep, attendance_rate calc
        WHERE pa.program_id = ep.program_id
          AND pa.application_id = calc.application_id
          AND ep.program_status = 'CLOSED'
          AND pa.application_status = 'APPROVED'
          AND pa.completion_status IS NULL
        """, nativeQuery = true)
    int judgeCompletion(@Param("now") Instant now);

    /**
     * ── 여기부터 "스태프용 신청자 목록 조회(Read)" 기능 ──────────────────────────────
     *
     * 스태프가 신청관리/이수판정 화면에서 프로그램 하나의 전체 신청자를 조회한다. student는 지연 로딩(LAZY)이고
     * 응답에 학생 이름/학번이 필요하므로 JOIN FETCH로 N+1을 방지한다(listMyApplications와 같은 이유).
     * status가 null이면 상태 필터 없이 전체 신청자를 조회한다.
     */
    @Query(value = """
        SELECT a FROM ProgramApplication a
        JOIN FETCH a.student
        WHERE a.program.programId = :programId
          AND (:status IS NULL OR a.applicationStatus = :status)
        """,
        countQuery = """
        SELECT COUNT(a) FROM ProgramApplication a
        WHERE a.program.programId = :programId
          AND (:status IS NULL OR a.applicationStatus = :status)
        """)
    Page<ProgramApplication> findAllByProgramIdAndStatus(@Param("programId") Integer programId,
                                                           @Param("status") String status, Pageable pageable);

    /**
     * ── 여기부터 "만족도 설문 완료 처리(Update)" 기능 ──────────────────────────────
     *
     * 개별 문항/응답 내용을 저장하는 기능은 이번 범위에서 제외되었고(저장할 엔티티 자체가 없음),
     * ProgramApplication.surveyCompleted 플래그만 true로 갱신한다. 승인(APPROVED)된 신청 건만
     * 제출할 수 있다는 조건을 WHERE 절에도 걸어, 서비스 계층의 확인과 이 UPDATE 사이의 경쟁 조건을 방지한다
     * (updateDecision/updateCancellation과 같은 패턴).
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE program_application
        SET survey_completed = true,
            updated_at = :now
        WHERE application_id = :applicationId
          AND student_id = :studentId
          AND application_status = 'APPROVED'
        """, nativeQuery = true)
    int markSurveyCompleted(@Param("applicationId") Integer applicationId,
                             @Param("studentId") Integer studentId,
                             @Param("now") Instant now);

    /**
     * ── 여기부터 "취소(Update)" 기능 ────────────────────────────────────────────
     *
     * updateDecision과 같은 이유로 native UPDATE를 쓴다. 다만 취소 가능 여부는 신청 건 하나만으로
     * 판단할 수 없고(모집 종료 시각은 program 테이블에 있음) program_application과
     * extracurricular_program을 조인해야 하므로, judgeCompletion과 같은 UPDATE ... FROM ... 구문을 쓴다.
     *
     * WHERE 절에 "p.recruitment_ends_at > :now"와 "pa.application_status IN (...)" 두 조건을
     * 함께 걸어둔 이유: 서비스 계층(ProgramApplicationService.cancel)에서 먼저 같은 조건을 확인하지만,
     * 그 확인과 이 UPDATE 실행 사이의 짧은 순간에 모집이 종료되거나 다른 요청이 먼저 처리(승인/반려/취소)했을
     * 수 있다(ExtracurricularProgramRepository.updateProgram과 동일한 경쟁 조건 방지 패턴).
     * 이 경우 UPDATE는 0개의 row에만 영향을 주고, 서비스 계층은 영향받은 row 수로 실패를 알아챈다.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE program_application pa
        SET application_status = 'CANCELLED',
            cancellation_reason = :reason,
            canceled_at = :now,
            updated_at = :now
        FROM extracurricular_program p
        WHERE pa.application_id = :applicationId
          AND pa.program_id = p.program_id
          AND p.recruitment_ends_at > :now
          AND pa.application_status IN ('APPLIED', 'WAITLISTED', 'APPROVED')
        """, nativeQuery = true)
    int updateCancellation(@Param("applicationId") Integer applicationId,
                            /** 취소 사유. 학생이 입력하지 않았으면 null. */
                            @Param("reason") String reason,
                            @Param("now") Instant now);
}
