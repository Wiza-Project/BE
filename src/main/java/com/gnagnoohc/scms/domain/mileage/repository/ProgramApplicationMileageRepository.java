package com.gnagnoohc.scms.domain.mileage.repository;

import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 비교과 도메인을 수정하지 않고 이수 완료 신청을 마일리지에서 읽는다. */
public interface ProgramApplicationMileageRepository extends JpaRepository<ProgramApplication, Integer> {

    /** 적립 직전 신청 건을 잠가 중복 실행을 줄인다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a
            from ProgramApplication a
            join fetch a.student
            join fetch a.program program
            join fetch program.competency programCompetency
            left join fetch program.mileagePolicy policy
            left join fetch policy.activityType activityType
            left join fetch activityType.competency
            where a.applicationId = :applicationId
              and a.completionStatus = 'COMPLETED'
            """)
    Optional<ProgramApplication> findCompletedForAccrual(
            @Param("applicationId") Integer applicationId
    );

    /** 스케줄러가 아직 적립되지 않은 이수 완료 신청을 배치로 찾는다. */
    @Query("""
            select a
            from ProgramApplication a
            join fetch a.student
            join fetch a.program program
            join fetch program.competency programCompetency
            left join fetch program.mileagePolicy policy
            left join fetch policy.activityType activityType
            left join fetch activityType.competency
            where a.completionStatus = 'COMPLETED'
              and not exists (
                  select t.mileageTransactionId
                  from MileageTransaction t
                  where t.sourceProgramApplication.applicationId = a.applicationId
              )
            order by a.completedAt asc, a.applicationId asc
            """)
    List<ProgramApplication> findCompletedWithoutMileage(Pageable pageable);
}
