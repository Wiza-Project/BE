package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingReservation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @EntityGraph(attributePaths = {"counselingType", "counselingSchedule"})
    Page<CounselingReservation> findAllByStudentUserId(Integer studentId, Pageable pageable);

    @EntityGraph(attributePaths = {"counselingType", "counselingSchedule"})
    Optional<CounselingReservation> findByCounselingReservationIdAndStudentUserId(
            Integer reservationId,
            Integer studentId
    );
}
