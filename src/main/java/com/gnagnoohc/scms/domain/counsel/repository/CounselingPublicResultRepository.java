package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.dto.StudentCounselingPublicResultRow;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingPublicResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 공개 상담 결과 조회·저장을 담당한다. 결과 자체는 별도 잠금이 필요 없다 — 회기(또는 최종 완료 시
 * 예약·배정) 행을 먼저 잠그는 CounselingPublicResultService의 쓰기 트랜잭션이 접근을 직렬화한다.
 */
public interface CounselingPublicResultRepository extends JpaRepository<CounselingPublicResult, Integer> {

    /** 체크리스트 9번 범위는 회기당 한 행(versionNo=1)만 존재하므로 상담사 조회·저장은 이 메서드로 충분하다. */
    Optional<CounselingPublicResult> findByCounselingSessionCounselingSessionId(Integer sessionId);

    /** 특정 버전을 지정 조회한다. 체크리스트 10번(정정·버전 추가)과 호환되는 모양으로 미리 둔다. */
    Optional<CounselingPublicResult> findByCounselingSessionCounselingSessionIdAndVersionNo(
            Integer sessionId, Integer versionNo
    );

    /**
     * 학생 본인의 공개 결과 목록. 회기별로 publishedAt이 있는 버전 중 최신(versionNo 최댓값)만 반환한다.
     * 체크리스트 9번은 회기당 버전이 하나뿐이라 상관 서브쿼리가 사실상 항상 참이지만, 10번에서 버전이
     * 늘어나도 이 쿼리 모양을 그대로 쓸 수 있도록 미리 최신 버전 선택 구조를 쓴다.
     */
    @Query("""
            select new com.gnagnoohc.scms.domain.counsel.dto.StudentCounselingPublicResultRow(
                r.publicResultId, s.counselingSessionId, res.counselingReservationId, s.sessionNo,
                ct.typeName, counselor.userName, s.startsAt, r.publishedAt, r.resultSummary, r.actionPlan
            )
            from CounselingPublicResult r
            join r.counselingSession s
            join s.counselingAssignment a
            join a.counselingReservation res
            join res.counselingType ct
            join a.counselor counselor
            where res.student.userId = :studentId
              and r.publishedAt is not null
              and r.versionNo = (
                  select max(r2.versionNo)
                  from CounselingPublicResult r2
                  where r2.counselingSession.counselingSessionId = s.counselingSessionId
                    and r2.publishedAt is not null
              )
            order by r.publishedAt desc, r.publicResultId desc
            """)
    Page<StudentCounselingPublicResultRow> findPublishedResultsForStudent(
            @Param("studentId") Integer studentId,
            Pageable pageable
    );

    /** 목록과 같은 소유권·최신 버전 조건으로 회기 하나의 상세를 조회한다. */
    @Query("""
            select new com.gnagnoohc.scms.domain.counsel.dto.StudentCounselingPublicResultRow(
                r.publicResultId, s.counselingSessionId, res.counselingReservationId, s.sessionNo,
                ct.typeName, counselor.userName, s.startsAt, r.publishedAt, r.resultSummary, r.actionPlan
            )
            from CounselingPublicResult r
            join r.counselingSession s
            join s.counselingAssignment a
            join a.counselingReservation res
            join res.counselingType ct
            join a.counselor counselor
            where res.student.userId = :studentId
              and s.counselingSessionId = :sessionId
              and r.publishedAt is not null
              and r.versionNo = (
                  select max(r2.versionNo)
                  from CounselingPublicResult r2
                  where r2.counselingSession.counselingSessionId = s.counselingSessionId
                    and r2.publishedAt is not null
              )
            """)
    Optional<StudentCounselingPublicResultRow> findPublishedResultForStudent(
            @Param("sessionId") Integer sessionId,
            @Param("studentId") Integer studentId
    );
}
