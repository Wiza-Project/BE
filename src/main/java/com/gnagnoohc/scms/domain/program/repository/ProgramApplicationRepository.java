package com.gnagnoohc.scms.domain.program.repository;

import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ProgramApplicationRepository extends JpaRepository<ProgramApplication, Integer> {

    // 신청 건 row에 비관적 락을 걸어 조회한다. 승인/반려 처리 중 같은 신청 건이 동시에
    // 이중 처리되는 경쟁 조건을 막기 위해 사용한다 (ExtracurricularProgramRepository.findByIdForUpdate와 동일 패턴).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM ProgramApplication a WHERE a.applicationId = :applicationId")
    Optional<ProgramApplication> findByIdForUpdate(@Param("applicationId") Integer applicationId);

    // ── 여기부터 "참여 신청 접수(Create)" 기능 ──────────────────────────────────────
    //
    // ProgramApplication 엔티티는 ExtracurricularProgram과 같은 이유로 protected 기본 생성자만 있고
    // 빌더/setter가 없어서, 애플리케이션 코드에서 인스턴스를 만들어 save()로 저장할 방법이 없다.
    // 그래서 native INSERT로 우회한다 (ExtracurricularProgramRepository.insertProgram 참고).
    @Query(value = """
        INSERT INTO program_application (
            program_id, student_id, application_status, waitlist_order, created_at, updated_at
        ) VALUES (
            :programId, :studentId, :applicationStatus, :waitlistOrder, :now, :now
        )
        RETURNING application_id
        """, nativeQuery = true)
    // RETURNING으로 새로 생성된 application_id를 그대로 돌려받아야 하므로 @Modifying을 붙이지 않는다.
    // program_id + student_id 조합은 uq_program_application_program_student 유니크 제약이 걸려있어,
    // 이미 신청한 학생이 다시 신청하면 이 INSERT가 DataIntegrityViolationException을 던진다
    // (서비스 계층에서 ErrorCode.ALREADY_APPLIED로 변환).
    Integer insertApplication(@Param("programId") Integer programId,
                               @Param("studentId") Integer studentId,
                               // "APPLIED" 또는 "WAITLISTED". 정원 비교 결과에 따라 서비스 계층이 결정한다.
                               @Param("applicationStatus") String applicationStatus,
                               // 정원 내 신청이면 null, 대기 신청이면 1부터 매겨지는 순번.
                               @Param("waitlistOrder") Integer waitlistOrder,
                               @Param("now") Instant now);

    // 특정 프로그램에서 특정 상태(주로 "APPLIED")인 신청 건수. 정원과 비교해 대기 여부를 판단하는 데 쓴다.
    long countByProgram_ProgramIdAndApplicationStatus(Integer programId, String applicationStatus);

    // 특정 프로그램의 현재까지 부여된 최대 대기순번. 대기 신청이 하나도 없으면 0을 반환한다
    // (다음 대기순번 = 이 값 + 1).
    @Query("""
        SELECT COALESCE(MAX(a.waitlistOrder), 0)
        FROM ProgramApplication a
        WHERE a.program.programId = :programId
          AND a.applicationStatus = 'WAITLISTED'
        """)
    Integer findMaxWaitlistOrderByProgramId(@Param("programId") Integer programId);

    // ── 여기부터 "승인/반려 처리(Update)" 기능 ──────────────────────────────────────
    //
    // ProgramApplication 엔티티는 setter/빌더가 없어(insertApplication과 같은 이유),
    // 승인/반려 상태 변경도 네이티브 UPDATE로 처리한다 (ExtracurricularProgramRepository.updateProgram 참고).
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
                        // "APPROVED" 또는 "REJECTED".
                        @Param("applicationStatus") String applicationStatus,
                        // 반려 사유. 승인이면 null.
                        @Param("decisionReason") String decisionReason,
                        // 처리한 운영부서 담당자의 user_id. 클라이언트가 보낸 값이 아니라
                        // 서비스 계층에서 "지금 로그인한 사용자"의 id를 그대로 전달한다.
                        @Param("processedBy") Integer processedBy,
                        @Param("now") Instant now);
}
