package com.gnagnoohc.scms.domain.mileage.DTO;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 학생 마일리지 대시보드에서 사용하는 조회 전용 응답 모델.
 * 엔티티를 그대로 노출하지 않고 화면에 필요한 값만 조합한다.
 */
public record MileageDashboardResponse(
        Period period,
        Summary summary,
        List<CompetencySummary> competencyBreakdown,
        List<SemesterTrendSummary> semesterTrend,
        List<TransactionSummary> recentTransactions,
        List<ClaimSummary> recentClaims
) {

    public record Period(
            String semesterCode
    ) {
    }

    /**
     * annualPoints는 currentSemesterPoints 중 연간(semesterCode='ALL') 정책 거래분이다.
     * semesterTrend는 ALL 정책 거래를 특정 학기에 임의 배정하지 않고 제외하므로,
     * semesterTrend의 선택 학기 항목 + annualPoints = currentSemesterPoints 관계가 성립한다.
     */
    public record Summary(
            BigDecimal currentSemesterPoints,
            BigDecimal annualPoints,
            BigDecimal cumulativePoints,
            Instant lastPostedAt
    ) {
    }

    public record CompetencySummary(
            Integer competencyId,
            String competencyName,
            BigDecimal points
    ) {
    }

    /** 선택 학기를 포함한 최근 적립 학기 추이. */
    public record SemesterTrendSummary(
            String semesterCode,
            BigDecimal points
    ) {
    }

    public record TransactionSummary(
            Integer transactionId,
            String activityName,
            String transactionType,
            BigDecimal points,
            String transactionStatus,
            Instant occurredAt
    ) {
    }

    public record ClaimSummary(
            Integer externalClaimId,
            String activityName,
            BigDecimal requestedPoints,
            BigDecimal policyPoints,
            BigDecimal grantedPoints,
            Instant applicationDate,
            String claimStatus,
            String rejectionReason
    ) {
    }
}
