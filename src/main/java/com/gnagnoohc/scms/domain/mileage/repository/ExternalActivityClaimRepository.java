package com.gnagnoohc.scms.domain.mileage.repository;

import com.gnagnoohc.scms.domain.mileage.entity.ExternalActivityClaim;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 학생이 신청한 외부활동 실적의 최근 처리 상태를 조회한다. */
public interface ExternalActivityClaimRepository extends JpaRepository<ExternalActivityClaim, Integer> {

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

    interface ClaimSummaryProjection {
        Integer getExternalClaimId();

        String getActivityName();

        BigDecimal getRequestedPoints();

        String getClaimStatus();

        Instant getApplicationDate();

        String getRejectionReason();
    }
}
