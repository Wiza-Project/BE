package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingSessionRow;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 상담 회기 조회·채번·중복 검사와 잠금을 담당한다. 목록·상세는 신청 원문·비공개 기록·공개 결과·연락처를
 * 포함하지 않는 {@link CounselingSessionRow} 프로젝션으로만 조회한다.
 */
public interface CounselingSessionRepository extends JpaRepository<CounselingSession, Integer> {

    /**
     * 후속 회기 생성·완료·취소 트랜잭션이 공유하는 잠금 조회다. 연관 엔티티를 fetch하지 않고
     * 회기 행 자체만 잠근다(소유권·상태 검증은 이미 잠근 활성 배정 또는 이 행의 assignment로 서비스가 수행).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CounselingSession s where s.counselingSessionId = :sessionId")
    Optional<CounselingSession> findByIdForUpdate(@Param("sessionId") Integer sessionId);

    /** 후속 회기 채번(MAX(sessionNo)+1)에 사용한다. 배정 행 잠금 이후에만 호출해야 정확하다. */
    @Query("select max(s.sessionNo) from CounselingSession s where s.counselingAssignment.counselingAssignmentId = :assignmentId")
    Optional<Integer> findMaxSessionNo(@Param("assignmentId") Integer assignmentId);

    /**
     * 같은 상담사의 CANCELED가 아닌 회기와 반열린 구간 [startsAt, endsAt) 기준으로 겹치는지 확인한다.
     * endsAt이 아직 없는(설계상 발생하지 않는) 회기는 시간을 점유한다고 볼 수 없으므로 비교 대상에서 뺀다.
     */
    @Query("""
            select case when count(s) > 0 then true else false end
            from CounselingSession s
            join s.counselingAssignment a
            where a.counselor.userId = :counselorId
              and s.sessionStatus <> 'CANCELED'
              and s.endsAt is not null
              and s.startsAt < :endsAt
              and s.endsAt > :startsAt
            """)
    boolean existsOverlappingSessionForCounselor(
            @Param("counselorId") Integer counselorId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );

    /**
     * 로그인 상담사의 현재·과거 배정에 연결된 회기 목록. 선택 필터(sessionStatus/from/to)는
     * null이면 조건을 적용하지 않는다. 신청 원문·비공개 기록·공개 결과·연락처는 프로젝션에 없다.
     */
    @Query("""
            select new com.gnagnoohc.scms.domain.counsel.dto.CounselingSessionRow(
                s.counselingSessionId, a.counselingAssignmentId, r.counselingReservationId, s.sessionNo,
                student.userId, student.universityNo, student.userName, department.codeName,
                counselingType.typeName, s.startsAt, s.endsAt, s.attendanceStatus, s.sessionStatus,
                s.nextSessionAt, s.cancellationReason, a.assignedAt, a.endedAt, counselor.userId
            )
            from CounselingSession s
            join s.counselingAssignment a
            join a.counselingReservation r
            join r.student student
            left join student.departmentCode department
            join r.counselingType counselingType
            join a.counselor counselor
            where counselor.userId = :counselorId
              and (:sessionStatus is null or s.sessionStatus = :sessionStatus)
              and (:from is null or s.startsAt >= :from)
              and (:to is null or s.startsAt < :to)
            order by s.startsAt desc, s.counselingSessionId desc
            """)
    Page<CounselingSessionRow> findSessions(
            @Param("counselorId") Integer counselorId,
            @Param("sessionStatus") String sessionStatus,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    /**
     * 회기 상세·생성/완료/취소 응답 조립에 쓰는 단건 프로젝션이다. counselorId 조건을 where에 포함해
     * 다른 상담사의 회기는 조회 자체가 안 되게 하고(소유권 비노출), 서비스는 결과가 비면 S007로 막는다.
     */
    @Query("""
            select new com.gnagnoohc.scms.domain.counsel.dto.CounselingSessionRow(
                s.counselingSessionId, a.counselingAssignmentId, r.counselingReservationId, s.sessionNo,
                student.userId, student.universityNo, student.userName, department.codeName,
                counselingType.typeName, s.startsAt, s.endsAt, s.attendanceStatus, s.sessionStatus,
                s.nextSessionAt, s.cancellationReason, a.assignedAt, a.endedAt, counselor.userId
            )
            from CounselingSession s
            join s.counselingAssignment a
            join a.counselingReservation r
            join r.student student
            left join student.departmentCode department
            join r.counselingType counselingType
            join a.counselor counselor
            where s.counselingSessionId = :sessionId
              and counselor.userId = :counselorId
            """)
    Optional<CounselingSessionRow> findDetailRow(
            @Param("sessionId") Integer sessionId,
            @Param("counselorId") Integer counselorId
    );

    /**
     * 최종 완료는 예약 전체(종료된 과거 배정 포함) 이력에 미완료 회기가 없어야 하므로, 배정 활성
     * 여부로 필터링하지 않고 예약에 딸린 모든 회기 중 PLANNED가 있는지 확인한다.
     */
    @Query("""
            select case when count(s) > 0 then true else false end
            from CounselingSession s
            where s.counselingAssignment.counselingReservation.counselingReservationId = :reservationId
              and s.sessionStatus = 'PLANNED'
            """)
    boolean existsPlannedSessionByReservationId(@Param("reservationId") Integer reservationId);

    /**
     * 지정한 배정 안에서 가장 늦게 끝난 COMPLETED+PRESENT 회기 하나를 endsAt DESC, counselingSessionId
     * DESC 순으로 찾는다. JPQL 서브쿼리는 LIMIT을 직접 쓸 수 없으므로 Pageable로 한 건만 가져오는
     * 방식을 쓰고, 최종 완료 대상 판정에 쓰는 findLatestCompletedPresentSessionId가 이를 감싼다.
     */
    @Query("""
            select s.counselingSessionId
            from CounselingSession s
            where s.counselingAssignment.counselingAssignmentId = :assignmentId
              and s.sessionStatus = 'COMPLETED'
              and s.attendanceStatus = 'PRESENT'
            order by s.endsAt desc, s.counselingSessionId desc
            """)
    List<Integer> findLatestCompletedPresentSessionIds(
            @Param("assignmentId") Integer assignmentId,
            Pageable pageable
    );

    default Optional<Integer> findLatestCompletedPresentSessionId(Integer assignmentId) {
        List<Integer> ids = findLatestCompletedPresentSessionIds(assignmentId, PageRequest.of(0, 1));
        return ids.isEmpty() ? Optional.empty() : Optional.of(ids.get(0));
    }

    /**
     * 여러 예약의 COMPLETED+PRESENT 회기 후보를 한 번에 가져오는 배치 조회다. 상담사·학생 응답의
     * finalResult(예약이 COMPLETED이고 이 회기가 그 예약의 마지막 출석 완료 회기인지)를 계산할 때,
     * 예약마다 따로 조회하면 목록 페이지 크기만큼 쿼리가 늘어나므로(N+1) 배치로 한 번에 가져와
     * 서비스에서 예약별로 (endsAt desc, sessionId desc) 최댓값을 골라내게 한다.
     */
    @Query("""
            select s.counselingAssignment.counselingReservation.counselingReservationId as reservationId,
                   s.counselingSessionId as sessionId,
                   s.endsAt as endsAt
            from CounselingSession s
            where s.counselingAssignment.counselingReservation.counselingReservationId in :reservationIds
              and s.sessionStatus = 'COMPLETED'
              and s.attendanceStatus = 'PRESENT'
            """)
    List<CompletedPresentSessionCandidate> findCompletedPresentSessionCandidates(
            @Param("reservationIds") Collection<Integer> reservationIds
    );

    /** findCompletedPresentSessionCandidates 전용 프로젝션. */
    interface CompletedPresentSessionCandidate {
        Integer getReservationId();
        Integer getSessionId();
        Instant getEndsAt();
    }
}
