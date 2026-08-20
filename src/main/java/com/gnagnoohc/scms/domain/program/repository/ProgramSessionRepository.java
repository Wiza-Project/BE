package com.gnagnoohc.scms.domain.program.repository;

import com.gnagnoohc.scms.domain.program.entity.ProgramSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProgramSessionRepository extends JpaRepository<ProgramSession, Integer> {

    // ProgramSession 엔티티는 protected 기본 생성자만 있고 빌더/setter가 없어서
    // (ExtracurricularProgramRepository.insertProgram과 같은 이유) native INSERT로 우회한다.
    @Query(value = """
        INSERT INTO program_session (
            program_id, session_no, session_name, starts_at, ends_at, location, created_by, created_at
        ) VALUES (
            :programId, :sessionNo, :sessionName, :startsAt, :endsAt, :location, :createdBy, :now
        )
        RETURNING program_session_id
        """, nativeQuery = true)
    // program_id + session_no 조합은 uq_program_session_program_no 유니크 제약이 걸려있어,
    // 이미 존재하는 회차 번호로 등록하면 이 INSERT가 DataIntegrityViolationException을 던진다
    // (서비스 계층에서 ErrorCode.DUPLICATE_SESSION_NO로 변환).
    Integer insertSession(@Param("programId") Integer programId,
                           @Param("sessionNo") Integer sessionNo,
                           @Param("sessionName") String sessionName,
                           @Param("startsAt") Instant startsAt,
                           @Param("endsAt") Instant endsAt,
                           @Param("location") String location,
                           @Param("createdBy") Integer createdBy,
                           @Param("now") Instant now);

    List<ProgramSession> findByProgram_ProgramIdOrderBySessionNoAsc(Integer programId);

    Optional<ProgramSession> findByProgramSessionIdAndProgram_ProgramId(Integer programSessionId, Integer programId);
}
