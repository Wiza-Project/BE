package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.dto.CounselingScheduleAvailabilityResponse;
import com.gnagnoohc.scms.domain.counsel.dto.CounselorScheduleResponse;
import com.gnagnoohc.scms.domain.counsel.entity.CounselingSchedule;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 상담 일정 저장과 일정 변경에 필요한 잠금·겹침 조회를 담당한다.
 */
public interface CounselingScheduleRepository extends JpaRepository<CounselingSchedule, Integer> {

    /**
     * 상담사 본인 일정과 예약 이력 존재 여부를 한 번에 조회한다.
     * 예약 상태와 관계없이 참조 행이 하나라도 있으면 수정이 막히므로 hasReservation도 같은 기준을 사용한다.
     * careerOnly가 true면 ST200+ST300 사용자용으로 CS200(진로상담) 일정만 조회 조건에서 걸러낸다.
     * 조회 후 메모리에서 다른 유형을 제거하지 않고 where 절에서 바로 제한한다.
     */
    @Query("""
            select new com.gnagnoohc.scms.domain.counsel.dto.CounselorScheduleResponse(
                schedule.counselingScheduleId,
                counselingType.counselingTypeId,
                counselor.userId,
                schedule.startsAt,
                schedule.endsAt,
                schedule.capacity,
                schedule.bookingDeadline,
                schedule.location,
                schedule.scheduleStatus,
                case when count(reservation.counselingReservationId) > 0 then true else false end
            )
            from CounselingSchedule schedule
            join schedule.counselingType counselingType
            join schedule.counselor counselor
            left join CounselingReservation reservation
              on reservation.counselingSchedule = schedule
            where counselor.userId = :counselorId
              and (:careerOnly = false or counselingType.typeCode = 'CS200')
            group by
                schedule.counselingScheduleId,
                counselingType.counselingTypeId,
                counselor.userId,
                schedule.startsAt,
                schedule.endsAt,
                schedule.capacity,
                schedule.bookingDeadline,
                schedule.location,
                schedule.scheduleStatus
            order by schedule.startsAt desc, schedule.counselingScheduleId desc
            """)
    List<CounselorScheduleResponse> findCounselorSchedules(
            @Param("counselorId") Integer counselorId,
            @Param("careerOnly") boolean careerOnly
    );

    /**
     * 일정과 점유 예약 수를 한 번에 집계해 목록 조회 중 추가 쿼리가 발생하지 않게 한다.
     * 반려·취소만 정원에서 제외하고 알 수 없는 상태는 보수적으로 정원을 점유한다.
     * 담당 상담사가 ST300도 함께 보유하면(ST200+ST300) 그 상담사의 일정 중 CS200(진로상담)만
     * 학생에게 노출한다. 합집합이 아니라 축소이므로 ST300을 아예 갖지 않은 상담사는 이 조건의
     * 영향을 받지 않고 기존처럼 활성 DIRECT 전체가 노출된다.
     */
    @Query("""
            select new com.gnagnoohc.scms.domain.counsel.dto.CounselingScheduleAvailabilityResponse(
                schedule.counselingScheduleId,
                counselor.userName,
                department.codeName,
                schedule.startsAt,
                schedule.endsAt,
                schedule.bookingDeadline,
                schedule.location,
                schedule.capacity,
                count(reservation.counselingReservationId)
            )
            from CounselingSchedule schedule
            join schedule.counselingType counselingType
            join schedule.counselor counselor
            left join counselor.departmentCode department
            left join CounselingReservation reservation
              on reservation.counselingSchedule = schedule
             and reservation.reservationStatus not in ('REJECTED', 'CANCELED')
            where counselingType.counselingTypeId = :counselingTypeId
              and counselingType.active = true
              and schedule.scheduleStatus = 'OPEN'
              and schedule.startsAt > :now
              and (schedule.bookingDeadline is null or schedule.bookingDeadline > :now)
              and counselor.accountStatus = 'ACTIVE'
              and counselor.userType = 'STAFF'
              and exists (
                  select role.id.userId
                  from UserRole role
                  where role.id.userId = counselor.userId
                    and role.id.roleCode = 'ST200'
              )
              and (
                  not exists (
                      select careerRole.id.userId
                      from UserRole careerRole
                      where careerRole.id.userId = counselor.userId
                        and careerRole.id.roleCode = 'ST300'
                  )
                  or counselingType.typeCode = 'CS200'
              )
            group by
                schedule.counselingScheduleId,
                counselor.userName,
                department.codeName,
                schedule.startsAt,
                schedule.endsAt,
                schedule.bookingDeadline,
                schedule.location,
                schedule.capacity
            having count(reservation.counselingReservationId) < schedule.capacity
            order by schedule.startsAt asc, schedule.counselingScheduleId asc
            """)
    List<CounselingScheduleAvailabilityResponse> findAvailableSchedules(
            @Param("counselingTypeId") Integer counselingTypeId,
            @Param("now") Instant now
    );

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
