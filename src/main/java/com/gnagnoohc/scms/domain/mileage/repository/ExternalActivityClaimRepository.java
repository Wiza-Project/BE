package com.gnagnoohc.scms.domain.mileage.repository;

import com.gnagnoohc.scms.domain.mileage.entity.ExternalActivityClaim;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 학생이 신청한 외부활동 실적의 최근 처리 상태를 조회한다. */
public interface ExternalActivityClaimRepository extends JpaRepository<ExternalActivityClaim, Integer> {

    /** 하나의 증빙 파일 그룹을 여러 외부활동 신청에서 재사용하지 못하게 확인한다. */
    boolean existsByFileGroup_FileGroupId(Integer fileGroupId);

    /** 교직원 심사 목록에 필요한 연관 데이터를 함께 조회한다. */
    @Query(value = """
            select c
            from ExternalActivityClaim c
            join fetch c.student
            join fetch c.activityType
            left join fetch c.mileagePolicy
            left join fetch c.fileGroup
            where (:status is null or c.claimStatus = :status)
              and (:keyword is null
                   or lower(c.activityName) like lower(concat('%', cast(:keyword as string), '%')) escape '!'
                   or lower(c.student.userName) like lower(concat('%', cast(:keyword as string), '%')) escape '!'
                   or lower(c.student.universityNo) like lower(concat('%', cast(:keyword as string), '%')) escape '!')
            order by c.createdAt desc, c.externalClaimId desc
            """,
            countQuery = """
            select count(c)
            from ExternalActivityClaim c
            where (:status is null or c.claimStatus = :status)
              and (:keyword is null
                   or lower(c.activityName) like lower(concat('%', cast(:keyword as string), '%')) escape '!'
                   or lower(c.student.userName) like lower(concat('%', cast(:keyword as string), '%')) escape '!'
                   or lower(c.student.universityNo) like lower(concat('%', cast(:keyword as string), '%')) escape '!')
            """)
    Page<ExternalActivityClaim> findForReview(
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    /** 승인·반려·취소의 경쟁 요청을 직렬화하기 위해 신청 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ExternalActivityClaim c where c.externalClaimId = :claimId")
    java.util.Optional<ExternalActivityClaim> findByIdForUpdate(@Param("claimId") Integer claimId);

    /** 교직원 상세 화면에 필요한 연관 데이터를 함께 조회한다. */
    @Query("""
            select c
            from ExternalActivityClaim c
            join fetch c.student
            join fetch c.activityType
            left join fetch c.mileagePolicy
            left join fetch c.fileGroup
            where c.externalClaimId = :claimId
            """)
    java.util.Optional<ExternalActivityClaim> findReviewDetailById(@Param("claimId") Integer claimId);

    /** 대시보드에 표시할 최근 외부활동 신청 내역을 최신순으로 조회한다. */
    @Query("""
            select c.externalClaimId as externalClaimId,
                   c.activityName as activityName,
                   c.requestedPoints as requestedPoints,
                   c.claimStatus as claimStatus,
                   c.createdAt as applicationDate,
                   c.reviewReason as rejectionReason
            from ExternalActivityClaim c
            where c.student.userId = :studentId
            order by c.createdAt desc
            """)
    List<ClaimSummaryProjection> findRecentClaims(
            @Param("studentId") Integer studentId,
            Pageable pageable
    );

    /** 최근 외부활동 신청 쿼리의 조회 전용 결과다. */
    interface ClaimSummaryProjection {
        Integer getExternalClaimId();

        String getActivityName();

        BigDecimal getRequestedPoints();

        String getClaimStatus();

        Instant getApplicationDate();

        String getRejectionReason();
    }
}
