package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.entity.CounselingSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * 상담 일정 저장과 일정 변경에 필요한 잠금·겹침 조회를 담당한다.
 */
public interface CounselingScheduleRepository extends JpaRepository<CounselingSchedule, Integer> {

    /**
     * 수정·마감과 예약 생성이 같은 일정을 동시에 바꾸지 못하도록 대상 행을 잠근다.
     * 이 잠금은 서비스 트랜잭션이 끝날 때까지 유지된다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select schedule
            from CounselingSchedule schedule
            where schedule.counselingScheduleId = :scheduleId
            """)
    Optional<CounselingSchedule> findByIdForUpdate(@Param("scheduleId") Integer scheduleId);

    /**
     * 반열린 구간 [시작, 종료) 기준으로 기존 시작이 새 종료보다 이르고 기존 종료가 새 시작보다 늦으면 겹친다.
     * 종료와 다음 시작이 같은 경계는 허용하며, 취소된 일정은 더 이상 시간을 점유하지 않는다.
     */
    @Query("""
            select case when count(schedule) > 0 then true else false end
            from CounselingSchedule schedule
            where schedule.counselor.userId = :counselorId
              and schedule.scheduleStatus <> 'CANCELED'
              and schedule.startsAt < :endsAt
              and schedule.endsAt > :startsAt
            """)
    boolean existsOverlappingSchedule(
            @Param("counselorId") Integer counselorId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );

    /**
     * 일정 수정 시 자기 자신은 비교 대상에서 빼고 다른 일정과의 겹침만 확인한다.
     */
    @Query("""
            select case when count(schedule) > 0 then true else false end
            from CounselingSchedule schedule
            where schedule.counselor.userId = :counselorId
              and schedule.counselingScheduleId <> :scheduleId
              and schedule.scheduleStatus <> 'CANCELED'
              and schedule.startsAt < :endsAt
              and schedule.endsAt > :startsAt
            """)
    boolean existsOverlappingScheduleExcluding(
            @Param("counselorId") Integer counselorId,
            @Param("scheduleId") Integer scheduleId,
            @Param("startsAt") Instant startsAt,
            @Param("endsAt") Instant endsAt
    );
}
