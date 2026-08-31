package com.gnagnoohc.scms.domain.program.repository;

import com.gnagnoohc.scms.domain.program.entity.ProgramSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ProgramSessionRepository extends JpaRepository<ProgramSession, Integer> {

    /**
     * ProgramSession 엔티티는 protected 기본 생성자만 있고 빌더/setter가 없어서
     * (ExtracurricularProgramRepository.insertProgram과 같은 이유) native INSERT로 우회한다.
     */
    @Query(value = """
        INSERT INTO program_session (
            program_id, session_no, session_name, starts_at, ends_at, location, created_by, created_at
        ) VALUES (
            :programId, :sessionNo, :sessionName, :startsAt, :endsAt, :location, :createdBy, :now
        )
        RETURNING program_session_id
        """, nativeQuery = true)
    /**
     * program_id + session_no 조합은 uq_program_session_program_no 유니크 제약이 걸려있어,
     * 이미 존재하는 회차 번호로 등록하면 이 INSERT가 DataIntegrityViolationException을 던진다
     * (서비스 계층에서 ErrorCode.DUPLICATE_SESSION_NO로 변환).
     */
    Integer insertSession(@Param("programId") Integer programId,
                           @Param("sessionNo") Integer sessionNo,
                           @Param("sessionName") String sessionName,
                           @Param("startsAt") Instant startsAt,
                           @Param("endsAt") Instant endsAt,
                           @Param("location") String location,
                           @Param("createdBy") Integer createdBy,
                           @Param("now") Instant now);

    List<ProgramSession> findByProgram_ProgramIdOrderBySessionNoAsc(Integer programId);

    // registerSession()이 새 회차 번호가 "현재 회차 수 + 1"인지(연속성 정책) 검증할 때 사용한다.
    long countByProgram_ProgramId(Integer programId);

    Optional<ProgramSession> findByProgramSessionIdAndProgram_ProgramId(Integer programSessionId, Integer programId);

    // "전회차와 동일" 장소 복사 시 직전 회차(sessionNo - 1)를 찾기 위한 조회 메서드.
    Optional<ProgramSession> findByProgram_ProgramIdAndSessionNo(Integer programId, Integer sessionNo);

    /**
     * updateSession()에서 회차 번호를 바꾸며 "전회차와 동일"을 쓰는 경우에 사용한다. 수정 대상 회차 자신은
     * 아직 UPDATE가 실행되기 전이라 DB에는 옛 sessionNo를 가진 채로 남아있으므로, 그냥 sessionNo로만 찾으면
     * 자기 자신이 "직전 회차"로 잘못 조회될 수 있다. excludeSessionId로 자기 자신을 제외해 이를 막는다.
     */
    Optional<ProgramSession> findByProgram_ProgramIdAndSessionNoAndProgramSessionIdNot(
            Integer programId, Integer sessionNo, Integer excludeSessionId);

    /**
     * insertSession과 같은 이유(엔티티에 setter/빌더 없음)로 native UPDATE로 우회한다.
     * WHERE 절에 program_id도 함께 걸어, 다른 프로그램 소속 회차를 실수로 건드리지 않게 한다.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE program_session
        SET session_no = :sessionNo, session_name = :sessionName,
            starts_at = :startsAt, ends_at = :endsAt, location = :location
        WHERE program_session_id = :sessionId AND program_id = :programId
        """, nativeQuery = true)
    int updateSession(@Param("sessionId") Integer sessionId,
                       @Param("programId") Integer programId,
                       @Param("sessionNo") Integer sessionNo,
                       @Param("sessionName") String sessionName,
                       @Param("startsAt") Instant startsAt,
                       @Param("endsAt") Instant endsAt,
                       @Param("location") String location);
}
