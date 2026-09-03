package com.gnagnoohc.scms.domain.mileage.repository;

import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 마일리지 거래 원장을 기준으로 대시보드 집계와 최근 내역을 조회한다. */
public interface MileageTransactionRepository extends JpaRepository<MileageTransaction, Integer> {

    /** 동일 비교과 신청으로 이미 생성된 적립 원장을 찾아 중복 적립을 막는다. */
    Optional<MileageTransaction> findBySourceProgramApplication_ApplicationId(Integer applicationId);

    /** 동일 외부활동 신청의 승인 적립 원장을 찾아 중복 적립을 막는다. */
    Optional<MileageTransaction> findBySourceExternalClaim_ExternalClaimId(Integer externalClaimId);

    /** 동일 역량진단 응시 회차로 이미 생성된 적립 원장을 찾아 중복 적립을 막는다. */
    Optional<MileageTransaction> findBySourceAssessmentAttempt_AttemptId(Integer attemptId);

    /** 동일 승인 원장에 이미 역분개가 생성됐는지 확인해 중복 취소를 막는다. */
    Optional<MileageTransaction> findByReversalOfTransaction_MileageTransactionId(Integer transactionId);

    /** 모든 학기의 확정 거래를 합산해 학생의 누적 마일리지를 조회한다. */
    @Query("""
            select coalesce(sum(t.points), 0)
            from MileageTransaction t
            where t.student.userId = :studentId
              and t.transactionStatus = 'POSTED'
            """)
    BigDecimal sumPostedPointsByStudent(@Param("studentId") Integer studentId);

    /** 지정한 학사기간 안의 확정 거래를 합산한다. */
    @Query("""
            select coalesce(sum(t.points), 0)
            from MileageTransaction t
            where t.student.userId = :studentId
              and t.transactionStatus = 'POSTED'
              and coalesce(t.postedAt, t.createdAt) >= :periodStart
              and coalesce(t.postedAt, t.createdAt) < :periodEnd
            """)
    BigDecimal sumPostedPointsByStudentBetween(
            @Param("studentId") Integer studentId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd
    );

    /** 지정한 학사기간 안에서 선택 학기 또는 ALL 정책에 귀속된 확정 거래를 합산한다. */
    @Query("""
            select coalesce(sum(t.points), 0)
            from MileageTransaction t
            join t.mileagePolicy p
            where t.student.userId = :studentId
              and t.transactionStatus = 'POSTED'
              and coalesce(t.postedAt, t.createdAt) >= :periodStart
              and coalesce(t.postedAt, t.createdAt) < :periodEnd
              and (p.semesterCode = :semesterCode or p.semesterCode = 'ALL')
            """)
    BigDecimal sumPostedPointsByStudentAndPeriod(
            @Param("studentId") Integer studentId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd,
            @Param("semesterCode") String semesterCode
    );

    /** 지정한 학사기간의 ALL 정책에 귀속된 확정 거래만 합산한다. */
    @Query("""
            select coalesce(sum(t.points), 0)
            from MileageTransaction t
            join t.mileagePolicy p
            where t.student.userId = :studentId
              and t.transactionStatus = 'POSTED'
              and coalesce(t.postedAt, t.createdAt) >= :periodStart
              and coalesce(t.postedAt, t.createdAt) < :periodEnd
              and p.semesterCode = 'ALL'
            """)
    BigDecimal sumPostedPointsByStudentAndAllSemester(
            @Param("studentId") Integer studentId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd
    );

    /** 확정일이 없는 기존 거래도 생성일을 사용해 마지막 적립 시점을 반환한다. */
    @Query("""
            select max(coalesce(t.postedAt, t.createdAt))
            from MileageTransaction t
            where t.student.userId = :studentId
              and t.transactionStatus = 'POSTED'
            """)
    Instant findLastPostedAt(@Param("studentId") Integer studentId);

    /** 선택 학사기간 안의 확정 거래를 정책의 학기 코드별로 DB에서 합산한다. */
    @Query("""
            select p.semesterCode as semesterCode,
                   coalesce(sum(t.points), 0) as points
            from MileageTransaction t
            join t.mileagePolicy p
            where t.student.userId = :studentId
              and t.transactionStatus = 'POSTED'
              and coalesce(t.postedAt, t.createdAt) >= :periodStart
              and coalesce(t.postedAt, t.createdAt) < :periodEnd
              and p.semesterCode <> 'ALL'
            group by p.semesterCode
            """)
    List<SemesterTrendProjection> findSemesterTrendByStudent(
            @Param("studentId") Integer studentId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd
    );

    /** 지정한 학사기간의 확정 적립 점수를 비교과 프로그램 유형별로 합산한다. */
    @Query("""
            select pt.codeName as programTypeName,
                   coalesce(sum(t.points), 0) as points
            from MileageTransaction t
            join t.mileagePolicy p
            join p.activityType activityType
            join activityType.programTypeCode pt
            where t.student.userId = :studentId
              and t.transactionStatus = 'POSTED'
              and coalesce(t.postedAt, t.createdAt) >= :periodStart
              and coalesce(t.postedAt, t.createdAt) < :periodEnd
              and (p.semesterCode = :semesterCode or p.semesterCode = 'ALL')
            group by pt.codeName
            order by sum(t.points) desc
            """)
    List<ProgramTypeSummaryProjection> findProgramTypeBreakdown(
            @Param("studentId") Integer studentId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd,
            @Param("semesterCode") String semesterCode
    );

    @Query("""
            select t.competency.competencyId as competencyId,
                   t.competency.competencyName as competencyName,
                   coalesce(sum(t.points), 0) as points
            from MileageTransaction t
            join t.mileagePolicy p
            where t.student.userId = :studentId
              and t.transactionStatus = 'POSTED'
              and coalesce(t.postedAt, t.createdAt) >= :periodStart
              and coalesce(t.postedAt, t.createdAt) < :periodEnd
              and (p.semesterCode = :semesterCode or p.semesterCode = 'ALL')
            group by t.competency.competencyId, t.competency.competencyName
            order by sum(t.points) desc
            """)
    List<CompetencySummaryProjection> findCompetencyBreakdown(
            @Param("studentId") Integer studentId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd,
            @Param("semesterCode") String semesterCode
    );

    /**
     * 거래 원장 기준의 최근 내역을 조회한다.
     *
     * <p>대기·반려 거래도 처리 상태를 화면에 보여줘야 하므로 POSTED만 필터링하지 않는다.
     * 또한 정책이 없는 수동 정정 거래가 있을 수 있어 mileagePolicy는 LEFT JOIN으로 조회한다.
     * 취소·정정 역분개는 원거래의 프로그램명 또는 외부활동명을 이어받는다.</p>
     */
    @Query("""
            select t.mileageTransactionId as transactionId,
                   t.transactionType as transactionType,
                   t.points as points,
                   t.transactionStatus as transactionStatus,
                   coalesce(
                       pa.program.programName,
                       ec.activityName,
                       reversalPa.program.programName,
                       reversalEc.activityName,
                       reversalActivityType.activityName,
                       activityType.activityName,
                       t.transactionReason,
                       '마일리지 정정'
                   ) as activityName,
                   coalesce(t.postedAt, t.createdAt) as occurredAt
            from MileageTransaction t
            left join t.mileagePolicy p
            left join p.activityType activityType
            left join t.sourceProgramApplication pa
            left join t.sourceExternalClaim ec
            left join t.reversalOfTransaction reversal
            left join reversal.sourceProgramApplication reversalPa
            left join reversal.sourceExternalClaim reversalEc
            left join reversal.mileagePolicy reversalPolicy
            left join reversalPolicy.activityType reversalActivityType
            where t.student.userId = :studentId
            order by coalesce(t.postedAt, t.createdAt) desc,
                     t.mileageTransactionId desc
            """)
    List<TransactionSummaryProjection> findRecentTransactions(
            @Param("studentId") Integer studentId,
            Pageable pageable
    );

    /**
     * 학생 본인의 확정 적립 거래를 최신순 페이지로 조회한다.
     * periodStart가 null이면 학기 필터 없이 전체 이력을 반환하고,
     * 지정되면 sumPostedPointsByStudentAndPeriod와 동일하게 해당 기간의 선택 학기 또는 ALL 정책 거래만 반환한다.
     */
    @Query(value = """
            select t.mileageTransactionId as transactionId,
                   t.transactionType as transactionType,
                   t.points as points,
                   t.transactionStatus as transactionStatus,
                   coalesce(
                       pa.program.programName,
                       ec.activityName,
                       reversalPa.program.programName,
                       reversalEc.activityName,
                       reversalActivityType.activityName,
                       activityType.activityName,
                       t.transactionReason,
                       '마일리지 정정'
                   ) as activityName,
                   case
                       when pa.applicationId is not null or reversalPa.applicationId is not null
                           then 'EXTRACURRICULAR_PROGRAM'
                       when ec.externalClaimId is not null or reversalEc.externalClaimId is not null
                           then 'EXTERNAL_ACTIVITY'
                       else 'OTHER'
                   end as sourceType,
                   coalesce(t.postedAt, t.createdAt) as occurredAt
            from MileageTransaction t
            left join t.mileagePolicy p
            left join p.activityType activityType
            left join t.sourceProgramApplication pa
            left join t.sourceExternalClaim ec
            left join t.reversalOfTransaction reversal
            left join reversal.sourceProgramApplication reversalPa
            left join reversal.sourceExternalClaim reversalEc
            left join reversal.mileagePolicy reversalPolicy
            left join reversalPolicy.activityType reversalActivityType
            where t.student.userId = :studentId
              and t.transactionType = 'EARN'
              and t.transactionStatus = 'POSTED'
              and (:periodStart is null
                   or (coalesce(t.postedAt, t.createdAt) >= :periodStart
                       and coalesce(t.postedAt, t.createdAt) < :periodEnd
                       and (p.semesterCode = :semesterCode or p.semesterCode = 'ALL')))
            order by coalesce(t.postedAt, t.createdAt) desc,
                     t.mileageTransactionId desc
            """,
            countQuery = """
                    select count(t)
                    from MileageTransaction t
                    left join t.mileagePolicy p
                    where t.student.userId = :studentId
                      and t.transactionType = 'EARN'
                      and t.transactionStatus = 'POSTED'
                      and (:periodStart is null
                           or (coalesce(t.postedAt, t.createdAt) >= :periodStart
                               and coalesce(t.postedAt, t.createdAt) < :periodEnd
                               and (p.semesterCode = :semesterCode or p.semesterCode = 'ALL')))
                    """)
    Page<TransactionHistoryProjection> findEarnedTransactions(
            @Param("studentId") Integer studentId,
            @Param("periodStart") Instant periodStart,
            @Param("periodEnd") Instant periodEnd,
            @Param("semesterCode") String semesterCode,
            Pageable pageable
    );

    /** 학생 본인의 확정 적립 거래 상세 조회에 사용하는 소유권 조건이다. */
    Optional<MileageTransaction> findByMileageTransactionIdAndStudent_UserIdAndTransactionTypeAndTransactionStatus(
            Integer transactionId,
            Integer studentId,
            String transactionType,
            String transactionStatus
    );

    /** 프로그램 유형별 점수 집계 쿼리의 조회 전용 결과다. */
    interface ProgramTypeSummaryProjection {
        String getProgramTypeName();

        BigDecimal getPoints();
    }

    /** 핵심역량별 점수 집계 쿼리의 조회 전용 결과다. */
    interface CompetencySummaryProjection {
        Integer getCompetencyId();

        String getCompetencyName();

        BigDecimal getPoints();
    }

    /** 최근 거래 원장 쿼리의 조회 전용 결과다. */
    interface TransactionSummaryProjection {
        Integer getTransactionId();

        String getTransactionType();

        BigDecimal getPoints();

        String getTransactionStatus();

        String getActivityName();

        Instant getOccurredAt();
    }

    /** 적립 원장 페이지 조회용 결과다. */
    interface TransactionHistoryProjection {
        Integer getTransactionId();

        String getActivityName();

        String getSourceType();

        String getTransactionType();

        BigDecimal getPoints();

        String getTransactionStatus();

        Instant getOccurredAt();
    }

    /** 학기별 적립 추이 계산에 사용하는 학기별 집계 결과다. */
    interface SemesterTrendProjection {
        String getSemesterCode();

        BigDecimal getPoints();
    }
}
