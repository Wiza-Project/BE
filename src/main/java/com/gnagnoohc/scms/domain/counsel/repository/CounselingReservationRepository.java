package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * 일정 수정 가능 여부를 판단할 때 필요한 예약 이력 조회를 담당한다.
 */
public interface CounselingReservationRepository extends JpaRepository<CounselingReservation, Integer> {

    /**
     * 취소·반려된 예약도 과거 이력이므로 상태와 관계없이 한 건이라도 있으면 일정 전체 수정을 막는다.
     */
    boolean existsByCounselingScheduleCounselingScheduleId(Integer scheduleId);

    /**
     * 거절·취소된 예약만 정원을 다시 사용하게 한다.
     * 그 밖의 상태는 실제 상담 진행 여부가 확정되기 전까지 자리를 점유한다.
     */
    @Query("""
            select count(reservation)
            from CounselingReservation reservation
            where reservation.counselingSchedule.counselingScheduleId = :scheduleId
              and reservation.reservationStatus not in ('REJECTED', 'CANCELED')
            """)
    long countOccupiedReservations(@Param("scheduleId") Integer scheduleId);

    /**
     * 학생 행 잠금을 얻은 뒤 호출해 같은 학생의 겹치는 유효 예약을 확인한다.
     * 종료 시각과 다음 시작 시각이 같은 경계는 겹침으로 보지 않는다.
     */
    @Query("""
            select case when count(reservation) > 0 then true else false end
            from CounselingReservation reservation
            join reservation.counselingSchedule schedule
            where reservation.student.userId = :studentId
              and reservation.reservationStatus not in ('REJECTED', 'CANCELED')
              and schedule.startsAt < :endsAt
              and schedule.endsAt > :startsAt
            """)
    boolean existsOverlappingActiveReservation(
            @Param("studentId") Integer studentId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );

    /**
     * 변경 시 본인 시간중복 재검증에서 아직 옛 일정을 참조 중인 예약 자기 자신은 제외해야 한다.
     * 제외하지 않으면 자신이 참조하던 옛 일정과 항상 겹쳐 오탐이 발생한다.
     */
    @Query("""
            select case when count(reservation) > 0 then true else false end
            from CounselingReservation reservation
            join reservation.counselingSchedule schedule
            where reservation.student.userId = :studentId
              and reservation.counselingReservationId <> :excludeReservationId
              and reservation.reservationStatus not in ('REJECTED', 'CANCELED')
              and schedule.startsAt < :endsAt
              and schedule.endsAt > :startsAt
            """)
    boolean existsOverlappingActiveReservationExcluding(
            @Param("studentId") Integer studentId,
            @Param("excludeReservationId") Integer excludeReservationId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );

    /**
     * 취소·변경은 예약 행 자체를 직렬화해야 하므로 조회와 동시에 쓰기 잠금을 건다.
     * 다른 학생의 예약인지와 없는 예약인지를 구분하지 않도록 studentId 조건을 함께 건다.
     * PESSIMISTIC_WRITE는 SQL의 SELECT ... FOR UPDATE로 변환된다. 이 행을 조회한 트랜잭션이
     * 커밋 또는 롤백으로 끝날 때까지 다른 트랜잭션은 같은 행에 대한 잠금 조회나 수정을 기다리게
     * 되므로, 같은 예약에 대한 취소·변경 요청이 동시에 들어와도 하나씩 순서대로 처리된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reservation
            from CounselingReservation reservation
            where reservation.counselingReservationId = :reservationId
              and reservation.student.userId = :studentId
            """)
    Optional<CounselingReservation> findByCounselingReservationIdAndStudentUserIdForUpdate(
            @Param("reservationId") Integer reservationId,
            @Param("studentId") Integer studentId
    );

    @EntityGraph(attributePaths = {"counselingType", "counselingSchedule"})
    Page<CounselingReservation> findAllByStudentUserId(Integer studentId, Pageable pageable);

    @EntityGraph(attributePaths = {"counselingType", "counselingSchedule"})
    Optional<CounselingReservation> findByCounselingReservationIdAndStudentUserId(
            Integer reservationId,
            Integer studentId
    );

    /**
     * 상담사의 승인·거절 처리는 학생 소유가 아니라 예약 자체를 잠가야 하므로 studentId 조건 없이 잠근다.
     * 실제 소유권(담당 일정의 상담사인지) 검증은 잠금 조회 이후 서비스가 schedule.counselor로 확인한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reservation
            from CounselingReservation reservation
            where reservation.counselingReservationId = :reservationId
            """)
    Optional<CounselingReservation> findByIdForUpdate(@Param("reservationId") Integer reservationId);

    /**
     * 로그인한 상담사의 일정에 걸린 REQUESTED 예약만 처리 대기 목록으로 보여준다.
     * counselingSchedule에 대한 inner join이므로, 일정이 없는 CENTER(센터 접수) 예약은
     * 자연히 이 목록에서 빠진다(현재 CENTER 신청 자체가 막혀 있어 실질적으로 발생하지 않는다).
     * DTO 변환(from)에서 counselingType·counselingSchedule·student를 바로 읽으므로
     * join fetch로 함께 가져와 목록 페이지마다 N+1 지연로딩 쿼리가 나가지 않게 한다.
     */
    @Query(value = """
            select r from CounselingReservation r
            join fetch r.counselingSchedule s
            join fetch r.counselingType
            join fetch r.student
            where s.counselor.userId = :counselorId
              and r.reservationStatus = 'REQUESTED'
            order by s.startsAt asc
            """,
           countQuery = """
            select count(r) from CounselingReservation r
            join r.counselingSchedule s
            where s.counselor.userId = :counselorId
              and r.reservationStatus = 'REQUESTED'
            """)
    Page<CounselingReservation> findPendingByCounselor(
            @Param("counselorId") Integer counselorId,
            Pageable pageable
    );

    /**
     * 상담사 상세 조회는 본인이 담당한 일정의 예약만 허용한다.
     * 다른 상담사의 예약인지와 없는 예약인지를 구분하지 않아 예약 존재를 노출하지 않는다.
     */
    @Query("""
            select r from CounselingReservation r
            join fetch r.counselingSchedule s
            join fetch r.counselingType
            join fetch r.student
            where r.counselingReservationId = :reservationId
              and s.counselor.userId = :counselorId
            """)
    Optional<CounselingReservation> findByIdAndCounselor(
            @Param("reservationId") Integer reservationId,
            @Param("counselorId") Integer counselorId
    );
}
