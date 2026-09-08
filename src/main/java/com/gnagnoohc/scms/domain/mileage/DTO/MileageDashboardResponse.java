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
     * semesterTrend는 ALL 정책 거래를 요청한 선택 학기(period.semesterCode) 항목에 합산해 포함하므로,
     * 다른 학기를 조회할 때는 ALL 정책 거래가 그 시점의 선택 학기로 다시 귀속된다.
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
