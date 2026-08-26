package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingSessionRow;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
}
