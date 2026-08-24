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
}
