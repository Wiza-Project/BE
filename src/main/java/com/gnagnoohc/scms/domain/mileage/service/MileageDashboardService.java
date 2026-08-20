package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import com.gnagnoohc.scms.domain.mileage.DTO.MileageDashboardResponse;
import com.gnagnoohc.scms.domain.mileage.repository.ExternalActivityClaimRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageBenefitApplicationRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageBenefitPolicyRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MileageDashboardService {

    private static final int RECENT_ITEM_LIMIT = 5;
    private static final int SEMESTER_TREND_LIMIT = 4;
    private static final String ALL_SEMESTER_CODE = "ALL";

    private final MileageTransactionRepository mileageTransactionRepository;
    private final ExternalActivityClaimRepository externalActivityClaimRepository;
    private final MileageBenefitPolicyRepository mileageBenefitPolicyRepository;
    private final MileageBenefitApplicationRepository mileageBenefitApplicationRepository;
    private final CompetencyRepository competencyRepository;

    public MileageDashboardResponse getDashboard(
            Integer studentId,
            Integer academicYear,
            String semesterCode
    ) {
        String selectedSemesterCode = validatePeriod(academicYear, semesterCode);

        BigDecimal currentSemesterPoints = valueOrZero(
                mileageTransactionRepository.sumPostedPointsByStudentAndPeriod(
                        studentId, academicYear, selectedSemesterCode));
        BigDecimal cumulativePoints = valueOrZero(
                mileageTransactionRepository.sumPostedPointsByStudent(studentId));

        PageRequest recentItems = PageRequest.of(0, RECENT_ITEM_LIMIT);

        var categoryBreakdown = mileageTransactionRepository
                .findCategoryBreakdown(studentId, academicYear, selectedSemesterCode)
                .stream()
                .map(item -> new MileageDashboardResponse.CategorySummary(
                        item.getCategoryCode(), item.getPoints()))
                .toList();

        var competencyBreakdown = getCompetencyBreakdown(
                studentId, academicYear, selectedSemesterCode);

        var benefitProgress = getBenefitProgress(
                studentId, academicYear, selectedSemesterCode, cumulativePoints);

        var semesterTrend = getSemesterTrend(studentId, academicYear, selectedSemesterCode);

        var recentTransactions = getRecentTransactions(studentId, recentItems);

        var recentClaims = getRecentExternalActivityApplications(studentId, recentItems);

        return new MileageDashboardResponse(
                new MileageDashboardResponse.Period(academicYear, selectedSemesterCode),
                new MileageDashboardResponse.Summary(
                        currentSemesterPoints,
                        cumulativePoints,
                        mileageTransactionRepository.findLastPostedAt(studentId)),
                benefitProgress,
                categoryBreakdown,
                competencyBreakdown,
                semesterTrend,
                recentTransactions,
                recentClaims
        );
    }

    public List<MileageDashboardResponse.TransactionSummary> getRecentTransactions(Integer studentId) {
        return getRecentTransactions(studentId, PageRequest.of(0, RECENT_ITEM_LIMIT));
    }

    public List<MileageDashboardResponse.ClaimSummary> getRecentExternalActivityApplications(
            Integer studentId
    ) {
        return getRecentExternalActivityApplications(
                studentId,
                PageRequest.of(0, RECENT_ITEM_LIMIT));
    }

    private List<MileageDashboardResponse.ClaimSummary> getRecentExternalActivityApplications(
            Integer studentId,
            PageRequest pageRequest
    ) {
        return externalActivityClaimRepository
                .findRecentClaims(studentId, pageRequest)
                .stream()
                .map(item -> new MileageDashboardResponse.ClaimSummary(
                        item.getExternalClaimId(),
                        item.getActivityName(),
                        item.getRequestedPoints(),
                        item.getApplicationDate(),
                        item.getClaimStatus(),
                        item.getRejectionReason()))
                .toList();
    }

    private List<MileageDashboardResponse.BenefitProgress> getBenefitProgress(
            Integer studentId,
            Integer academicYear,
            String semesterCode,
            BigDecimal cumulativePoints
    ) {
        var benefitPolicies = mileageBenefitPolicyRepository
                .findByActiveTrueAndAcademicYearAndSemesterCodeInOrderByMinimumPointsAsc(
                        academicYear, List.of(semesterCode, ALL_SEMESTER_CODE));

        if (benefitPolicies.isEmpty()) {
            return List.of();
        }

        var applicationStatuses = mileageBenefitApplicationRepository
                .findApplicationStatuses(
                        studentId,
                        benefitPolicies.stream()
                                .map(policy -> policy.getBenefitPolicyId())
                                .toList())
                .stream()
                .collect(Collectors.toMap(
                        MileageBenefitApplicationRepository.ApplicationStatusProjection::getBenefitPolicyId,
                        MileageBenefitApplicationRepository.ApplicationStatusProjection::getApplicationStatus,
                        (first, ignored) -> first));

        Instant now = Instant.now();
        return benefitPolicies.stream()
                .map(policy -> toBenefitProgress(policy, cumulativePoints,
                        applicationStatuses.get(policy.getBenefitPolicyId()), now))
                .toList();
    }

    private MileageDashboardResponse.BenefitProgress toBenefitProgress(
            com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy policy,
            BigDecimal cumulativePoints,
            String applicationStatus,
            Instant now
    ) {
        BigDecimal shortagePoints = policy.getMinimumPoints()
                .subtract(cumulativePoints)
                .max(BigDecimal.ZERO);
        String progressStatus = resolveBenefitProgressStatus(
                policy, shortagePoints, applicationStatus, now);

        return new MileageDashboardResponse.BenefitProgress(
                policy.getBenefitPolicyId(),
                policy.getBenefitType(),
                policy.getBenefitName(),
                policy.getMinimumPoints(),
                cumulativePoints,
                shortagePoints,
                policy.getBenefitAmount(),
                progressStatus,
                applicationStatus,
                "ELIGIBLE".equals(progressStatus)
        );
    }

    private String resolveBenefitProgressStatus(
            com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy policy,
            BigDecimal shortagePoints,
            String applicationStatus,
            Instant now
    ) {
        if (applicationStatus != null) {
            return "APPLIED";
        }
        if (shortagePoints.signum() > 0) {
            return "INSUFFICIENT_POINTS";
        }
        if (policy.getApplicationStartsAt() != null && now.isBefore(policy.getApplicationStartsAt())) {
            return "APPLICATION_NOT_OPEN";
        }
        if (policy.getApplicationEndsAt() != null && now.isAfter(policy.getApplicationEndsAt())) {
            return "APPLICATION_CLOSED";
        }
        return "ELIGIBLE";
    }

    private List<MileageDashboardResponse.CompetencySummary> getCompetencyBreakdown(
            Integer studentId,
            Integer academicYear,
            String semesterCode
    ) {
        Map<Integer, BigDecimal> pointsByCompetencyId = mileageTransactionRepository
                .findCompetencyBreakdown(studentId, academicYear, semesterCode)
                .stream()
                .collect(Collectors.toMap(
                        MileageTransactionRepository.CompetencySummaryProjection::getCompetencyId,
                        item -> valueOrZero(item.getPoints())));

        List<Competency> topLevelCompetencies = competencyRepository.findAll().stream()
                .filter(Competency::isActive)
                .filter(competency -> competency.getParentCompetency() == null)
                .sorted(Comparator.comparing(
                        Competency::getDisplayOrder,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();

        if (topLevelCompetencies.isEmpty()) {
            return mileageTransactionRepository
                    .findCompetencyBreakdown(studentId, academicYear, semesterCode)
                    .stream()
                    .map(item -> new MileageDashboardResponse.CompetencySummary(
                            item.getCompetencyId(), item.getCompetencyName(), item.getPoints()))
                    .toList();
        }

        return topLevelCompetencies.stream()
                .map(competency -> new MileageDashboardResponse.CompetencySummary(
                        competency.getCompetencyId(),
                        competency.getCompetencyName(),
                        pointsByCompetencyId.getOrDefault(
                                competency.getCompetencyId(), BigDecimal.ZERO)))
                .toList();
    }

    private List<MileageDashboardResponse.SemesterTrendSummary> getSemesterTrend(
            Integer studentId,
            Integer academicYear,
            String semesterCode
    ) {
        var selectedPeriod = new MileageDashboardResponse.SemesterTrendSummary(
                academicYear, semesterCode, BigDecimal.ZERO);

        List<MileageDashboardResponse.SemesterTrendSummary> trend = mileageTransactionRepository
                .findSemesterTrendByStudent(studentId)
                .stream()
                .map(item -> new MileageDashboardResponse.SemesterTrendSummary(
                        item.getAcademicYear(),
                        item.getSemesterCode(),
                        valueOrZero(item.getPoints())))
                .filter(item -> comparePeriods(item, selectedPeriod) <= 0)
                .collect(Collectors.toMap(
                        item -> item.academicYear() + ":" + item.semesterCode(),
                        Function.identity(),
                        (first, ignored) -> first))
                .values()
                .stream()
                .collect(Collectors.toList());

        boolean selectedPeriodExists = trend.stream().anyMatch(item ->
                item.academicYear().equals(academicYear)
                        && item.semesterCode().equals(semesterCode));
        if (!selectedPeriodExists) {
            trend.add(selectedPeriod);
        }

        return trend.stream()
                .sorted(this::comparePeriods)
                .skip(Math.max(0, trend.size() - SEMESTER_TREND_LIMIT))
                .toList();
    }

    private int comparePeriods(
            MileageDashboardResponse.SemesterTrendSummary first,
            MileageDashboardResponse.SemesterTrendSummary second
    ) {
        int academicYearComparison = first.academicYear().compareTo(second.academicYear());
        if (academicYearComparison != 0) {
            return academicYearComparison;
        }

        int semesterOrderComparison = Integer.compare(
                semesterOrder(first.semesterCode()), semesterOrder(second.semesterCode()));
        if (semesterOrderComparison != 0) {
            return semesterOrderComparison;
        }
        return first.semesterCode().compareTo(second.semesterCode());
    }

    private int semesterOrder(String semesterCode) {
        return switch (semesterCode.toUpperCase(Locale.ROOT)) {
            case "1", "1ST", "FIRST", "SPRING" -> 1;
            case "SUMMER" -> 2;
            case "2", "2ND", "SECOND", "FALL" -> 3;
            case "WINTER" -> 4;
            default -> 100;
        };
    }

    private String validatePeriod(Integer academicYear, String semesterCode) {
        if (academicYear == null || academicYear < 2000 || academicYear > 9999
                || semesterCode == null || semesterCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 학기 정보가 올바르지 않습니다.");
        }

        String normalizedSemesterCode = semesterCode.trim();
        if (ALL_SEMESTER_CODE.equalsIgnoreCase(normalizedSemesterCode)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조회 학기는 개별 학기로 지정해야 합니다.");
        }
        return normalizedSemesterCode;
    }

    private List<MileageDashboardResponse.TransactionSummary> getRecentTransactions(
            Integer studentId,
            PageRequest pageRequest
    ) {
        return mileageTransactionRepository
                .findRecentTransactions(studentId, pageRequest)
                .stream()
                .map(item -> new MileageDashboardResponse.TransactionSummary(
                        item.getTransactionId(),
                        item.getActivityName(),
                        item.getTransactionType(),
                        item.getPoints(),
                        item.getTransactionStatus(),
                        item.getOccurredAt()))
                .toList();
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
