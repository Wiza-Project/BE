package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import com.gnagnoohc.scms.domain.mileage.DTO.MileageDashboardResponse;
import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy;
import com.gnagnoohc.scms.domain.mileage.repository.ExternalActivityClaimRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageBenefitApplicationRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageBenefitPolicyRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MileageDashboardServiceTest {

    @Mock
    private MileageTransactionRepository mileageTransactionRepository;
    @Mock
    private ExternalActivityClaimRepository externalActivityClaimRepository;
    @Mock
    private MileageBenefitPolicyRepository mileageBenefitPolicyRepository;
    @Mock
    private MileageBenefitApplicationRepository mileageBenefitApplicationRepository;
    @Mock
    private CompetencyRepository competencyRepository;

    private MileageDashboardService mileageDashboardService;

    @BeforeEach
    void setUp() {
        mileageDashboardService = new MileageDashboardService(
                mileageTransactionRepository,
                externalActivityClaimRepository,
                mileageBenefitPolicyRepository,
                mileageBenefitApplicationRepository,
                competencyRepository
        );
    }

    @Test
    void dashboard_usesCumulativePostedPointsForBenefitProgressAndFillsZeroPointCompetencies() {
        MileageBenefitPolicy scholarshipPolicy = benefitPolicy(
                1, "SCHOLARSHIP", "장학금 신청", "1000", "200000");
        MileageBenefitPolicy excellentPolicy = benefitPolicy(
                2, "SCHOLARSHIP", "우수 마일리지 장학", "1500", "500000");

        MileageBenefitApplicationRepository.ApplicationStatusProjection application = mock(
                MileageBenefitApplicationRepository.ApplicationStatusProjection.class);
        when(application.getBenefitPolicyId()).thenReturn(1);
        when(application.getApplicationStatus()).thenReturn("APPLIED");

        Competency selfManagement = competency(1, "자기관리", 1);
        Competency communication = competency(2, "의사소통", 2);
        MileageTransactionRepository.CompetencySummaryProjection selfManagementPoints = mock(
                MileageTransactionRepository.CompetencySummaryProjection.class);
        when(selfManagementPoints.getCompetencyId()).thenReturn(1);
        when(selfManagementPoints.getPoints()).thenReturn(new BigDecimal("92"));

        MileageTransactionRepository.SemesterTrendProjection previousSemester = trend(
                2025, "2", "320");
        MileageTransactionRepository.SemesterTrendProjection selectedSemester = trend(
                2026, "1", "430");

        when(mileageTransactionRepository.sumPostedPointsByStudentAndPeriod(10, 2026, "1"))
                .thenReturn(new BigDecimal("430"));
        when(mileageTransactionRepository.sumPostedPointsByStudent(10))
                .thenReturn(new BigDecimal("1250"));
        when(mileageTransactionRepository.findCategoryBreakdown(10, 2026, "1"))
                .thenReturn(List.of());
        when(mileageTransactionRepository.findCompetencyBreakdown(10, 2026, "1"))
                .thenReturn(List.of(selfManagementPoints));
        when(mileageTransactionRepository.findSemesterTrendByStudent(10))
                .thenReturn(List.of(previousSemester, selectedSemester));
        when(mileageTransactionRepository.findRecentTransactions(eq(10), any()))
                .thenReturn(List.of());
        when(externalActivityClaimRepository.findRecentClaims(eq(10), any()))
                .thenReturn(List.of());
        when(mileageBenefitPolicyRepository
                .findByActiveTrueAndAcademicYearAndSemesterCodeInOrderByMinimumPointsAsc(
                        eq(2026), any()))
                .thenReturn(List.of(scholarshipPolicy, excellentPolicy));
        when(mileageBenefitApplicationRepository.findApplicationStatuses(eq(10), any()))
                .thenReturn(List.of(application));
        when(competencyRepository.findAll()).thenReturn(List.of(selfManagement, communication));

        MileageDashboardResponse response = mileageDashboardService.getDashboard(10, 2026, "1");

        assertThat(response.summary().currentSemesterPoints()).isEqualByComparingTo("430");
        assertThat(response.summary().cumulativePoints()).isEqualByComparingTo("1250");
        assertThat(response.summary().grade().currentGrade()).isEqualTo("GOLD");
        assertThat(response.summary().grade().goldMinimumPoints()).isEqualByComparingTo("1000");
        assertThat(response.summary().grade().pointsToGold()).isEqualByComparingTo("0");
        assertThat(response.summary().grade().goldProgressPercent()).isEqualByComparingTo("100");
        assertThat(response.benefitProgress())
                .extracting(MileageDashboardResponse.BenefitProgress::progressStatus)
                .containsExactly("APPLIED", "INSUFFICIENT_POINTS");
        assertThat(response.benefitProgress().get(1).shortagePoints()).isEqualByComparingTo("250");
        assertThat(response.competencyBreakdown())
                .extracting(MileageDashboardResponse.CompetencySummary::points)
                .containsExactly(new BigDecimal("92"), BigDecimal.ZERO);
        assertThat(response.semesterTrend())
                .extracting(MileageDashboardResponse.SemesterTrendSummary::semesterCode)
                .containsExactly("2", "1");
    }

    @Test
    void dashboard_showsStandardAndRemainingPointsBelowGoldThreshold() {
        when(mileageTransactionRepository.sumPostedPointsByStudentAndPeriod(10, 2026, "1"))
                .thenReturn(new BigDecimal("250"));
        when(mileageTransactionRepository.sumPostedPointsByStudent(10))
                .thenReturn(new BigDecimal("850"));
        when(mileageTransactionRepository.findCategoryBreakdown(10, 2026, "1"))
                .thenReturn(List.of());
        when(mileageTransactionRepository.findCompetencyBreakdown(10, 2026, "1"))
                .thenReturn(List.of());
        when(mileageTransactionRepository.findSemesterTrendByStudent(10))
                .thenReturn(List.of());
        when(mileageTransactionRepository.findRecentTransactions(eq(10), any()))
                .thenReturn(List.of());
        when(externalActivityClaimRepository.findRecentClaims(eq(10), any()))
                .thenReturn(List.of());
        when(mileageBenefitPolicyRepository
                .findByActiveTrueAndAcademicYearAndSemesterCodeInOrderByMinimumPointsAsc(
                        eq(2026), any()))
                .thenReturn(List.of());
        when(competencyRepository.findAll()).thenReturn(List.of());

        MileageDashboardResponse response = mileageDashboardService.getDashboard(10, 2026, "1");

        assertThat(response.summary().grade().currentGrade()).isEqualTo("STANDARD");
        assertThat(response.summary().grade().currentPoints()).isEqualByComparingTo("850");
        assertThat(response.summary().grade().pointsToGold()).isEqualByComparingTo("150");
        assertThat(response.summary().grade().goldProgressPercent()).isEqualByComparingTo("85");
    }

    @Test
    void dashboard_rejectsAllAsASelectedSemester() {
        assertThatThrownBy(() -> mileageDashboardService.getDashboard(10, 2026, "ALL"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("조회 학기는 개별 학기로 지정해야 합니다.");
    }

    private MileageBenefitPolicy benefitPolicy(
            Integer policyId,
            String benefitType,
            String benefitName,
            String minimumPoints,
            String benefitAmount
    ) {
        MileageBenefitPolicy policy = mock(MileageBenefitPolicy.class);
        when(policy.getBenefitPolicyId()).thenReturn(policyId);
        when(policy.getBenefitType()).thenReturn(benefitType);
        when(policy.getBenefitName()).thenReturn(benefitName);
        when(policy.getMinimumPoints()).thenReturn(new BigDecimal(minimumPoints));
        when(policy.getBenefitAmount()).thenReturn(new BigDecimal(benefitAmount));
        return policy;
    }

    private Competency competency(Integer competencyId, String competencyName, Integer displayOrder) {
        Competency competency = mock(Competency.class);
        when(competency.getCompetencyId()).thenReturn(competencyId);
        when(competency.getCompetencyName()).thenReturn(competencyName);
        when(competency.getDisplayOrder()).thenReturn(displayOrder);
        when(competency.isActive()).thenReturn(true);
        return competency;
    }

    private MileageTransactionRepository.SemesterTrendProjection trend(
            Integer academicYear,
            String semesterCode,
            String points
    ) {
        MileageTransactionRepository.SemesterTrendProjection trend = mock(
                MileageTransactionRepository.SemesterTrendProjection.class);
        when(trend.getAcademicYear()).thenReturn(academicYear);
        when(trend.getSemesterCode()).thenReturn(semesterCode);
        when(trend.getPoints()).thenReturn(new BigDecimal(points));
        return trend;
    }
}
