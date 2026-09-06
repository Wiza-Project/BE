package com.gnagnoohc.scms.domain.counsel.repository;

import com.gnagnoohc.scms.domain.counsel.dto.response.CounselingScheduleAvailabilityResponse;
import com.gnagnoohc.scms.domain.counsel.dto.response.CounselorScheduleResponse;
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
     * careerOnly가 true면 ST300 단독(CAREER_ONLY) 사용자용으로 CS200(진로상담) 일정만 조회 조건에서 걸러낸다.
     * 조회 후 메모리에서 다른 유형을 제거하지 않고 where 절에서 바로 제한한다.
     * 신청 경로가 DIRECT인 일정만 조회한다. 단건 조작 판정(CounselManagementAccessPolicy.allows)이
     * route≠DIRECT를 거부하는 것과 목록 술어를 일치시켜, "목록엔 보이는데 다룰 수 없는 일정"이
     * 생기지 않게 한다(일정 생성 자체가 DIRECT만 허용하므로 실질 결과는 같지만 규칙을 한 곳과 맞춘다).
     * remainingCapacity는 REJECTED·CANCELED를 제외한 점유 예약 수를 정원에서 뺀 값이며,
     * 기존 데이터가 정원을 초과했더라도 0 밑으로 내려가지 않게 CASE로 한 번 더 감싼다.
     * hasReservation과 같은 LEFT JOIN 결과를 재사용하므로 건당 추가 쿼리가 생기지 않는다.
     */
    @Query("""
            select new com.gnagnoohc.scms.domain.counsel.dto.response.CounselorScheduleResponse(
                schedule.counselingScheduleId,
                counselingType.counselingTypeId,
                counselor.userId,
                schedule.startsAt,
                schedule.endsAt,
                schedule.capacity,
                schedule.bookingDeadline,
                schedule.location,
                schedule.scheduleStatus,
                case when count(reservation.counselingReservationId) > 0 then true else false end,
                case
                    when (schedule.capacity - sum(case when reservation.reservationStatus not in ('REJECTED', 'CANCELED') then 1 else 0 end)) > 0
                    then cast((schedule.capacity - sum(case when reservation.reservationStatus not in ('REJECTED', 'CANCELED') then 1 else 0 end)) as int)
                    else 0
                end
            )
            from CounselingSchedule schedule
            join schedule.counselingType counselingType
            join schedule.counselor counselor
            left join CounselingReservation reservation
              on reservation.counselingSchedule = schedule
            where counselor.userId = :counselorId
              and counselingType.applicationRoute = 'DIRECT'
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
     *
     * <p>ST300 지도교수 진로상담 관리 리팩터링 설계(5.1장)에 따라 담당 상담사의 역할 범위를
     * 배타 조건으로 걸러낸다(ST200/ST300 동시 보유·무역할은 둘 다 배제되어 결과적으로 숨겨진다).</p>
     * <ul>
     *   <li>ST200만 보유: 지도교수 관계와 무관하게 기존처럼 활성 DIRECT 전체를 노출한다.</li>
     *   <li>ST300만 보유: CS200 유형이면서, 조회하는 학생의 학적 상세(student_academic_detail)의
     *       지도교수가 그 상담사 본인일 때만 노출한다. 학적 상세 행 자체가 없으면(지도교수 미입력)
     *       ST300 소유 일정은 하나도 보이지 않는다.</li>
     * </ul>
     */
    @Query("""
            select new com.gnagnoohc.scms.domain.counsel.dto.response.CounselingScheduleAvailabilityResponse(
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
              and (
                  (
                      exists (
                          select generalRole.id.userId
                          from UserRole generalRole
                          where generalRole.id.userId = counselor.userId
                            and generalRole.id.roleCode = 'ST200'
                      )
                      and not exists (
                          select careerRole.id.userId
                          from UserRole careerRole
                          where careerRole.id.userId = counselor.userId
                            and careerRole.id.roleCode = 'ST300'
                      )
                  )
                  or (
                      not exists (
                          select generalRole.id.userId
                          from UserRole generalRole
                          where generalRole.id.userId = counselor.userId
                            and generalRole.id.roleCode = 'ST200'
                      )
                      and exists (
                          select careerRole.id.userId
                          from UserRole careerRole
                          where careerRole.id.userId = counselor.userId
                            and careerRole.id.roleCode = 'ST300'
                      )
                      and counselingType.typeCode = 'CS200'
                      and exists (
                          select detail.userId
                          from StudentAcademicDetail detail
                          where detail.userId = :studentId
                            and detail.advisorUser.userId = counselor.userId
                      )
                  )
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
            @Param("now") Instant now,
            @Param("studentId") Integer studentId
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
