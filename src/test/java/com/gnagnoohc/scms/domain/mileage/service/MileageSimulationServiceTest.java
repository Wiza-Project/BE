package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.MileageSimulationResponse;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageSimulationRequest;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.repository.MileageBenefitPolicyRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileagePolicyRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MileageSimulationServiceTest {

    @Mock
    private MileageBenefitPolicyRepository benefitPolicyRepository;
    @Mock
    private MileagePolicyRepository mileagePolicyRepository;
    @Mock
    private MileageTransactionRepository mileageTransactionRepository;

    @InjectMocks
    private MileageSimulationService mileageSimulationService;

    @Test
    void simulate_addsPlannedActivitiesWithoutChangingActualPoints() {
        MileageBenefitPolicy benefitPolicy = benefitPolicy(10, "SCHOLARSHIP", "500");
        MileagePolicy volunteerPolicy = mileagePolicy(21, "VOLUNTEER", "봉사활동", "60", null);
        MileagePolicy contestPolicy = mileagePolicy(22, "CONTEST", "교내 공모전", "80", null);

        when(benefitPolicyRepository.findByBenefitPolicyIdAndActiveTrue(10))
                .thenReturn(Optional.of(benefitPolicy));
        when(mileageTransactionRepository.sumPostedPointsByStudentAndPeriod(7, 2026, "1"))
                .thenReturn(new BigDecimal("320"));
        when(mileagePolicyRepository.findSimulationPolicies(
                eq(2026), eq(List.of("1", "ALL")), any(LocalDate.class)))
                .thenReturn(List.of(volunteerPolicy, contestPolicy));

        MileageSimulationResponse.Result result = mileageSimulationService.simulate(
                7,
                new MileageSimulationRequest(
                        2026,
                        "1",
                        10,
                        null,
                        List.of(
                                new MileageSimulationRequest.PlannedActivity(21, 2),
                                new MileageSimulationRequest.PlannedActivity(22, 1))));

        assertThat(result.currentPoints()).isEqualByComparingTo("320");
        assertThat(result.plannedPoints()).isEqualByComparingTo("200");
        assertThat(result.projectedPoints()).isEqualByComparingTo("520");
        assertThat(result.shortagePoints()).isEqualByComparingTo("0");
        assertThat(result.achieved()).isTrue();
        assertThat(result.plannedActivities()).hasSize(2);
    }

    @Test
    void simulate_appliesMaximumPointsPerActivity() {
        MileagePolicy policy = mileagePolicy(21, "VOLUNTEER", "봉사활동", "60", "100");
        when(mileageTransactionRepository.sumPostedPointsByStudentAndPeriod(7, 2026, "1"))
                .thenReturn(new BigDecimal("0"));
        when(mileagePolicyRepository.findSimulationPolicies(
                eq(2026), eq(List.of("1", "ALL")), any(LocalDate.class)))
                .thenReturn(List.of(policy));

        MileageSimulationResponse.Result result = mileageSimulationService.simulate(
                7,
                new MileageSimulationRequest(
                        2026,
                        "1",
                        null,
                        new BigDecimal("150"),
                        List.of(new MileageSimulationRequest.PlannedActivity(21, 3))));

        assertThat(result.plannedPoints()).isEqualByComparingTo("100");
        assertThat(result.projectedPoints()).isEqualByComparingTo("100");
        assertThat(result.shortagePoints()).isEqualByComparingTo("50");
        assertThat(result.achieved()).isFalse();
    }

    @Test
    void simulate_supportsDirectTargetWithoutBenefitPolicy() {
        MileagePolicy policy = mileagePolicy(21, "VOLUNTEER", "봉사활동", "80", null);
        when(mileageTransactionRepository.sumPostedPointsByStudentAndPeriod(7, 2026, "1"))
                .thenReturn(new BigDecimal("320"));
        when(mileagePolicyRepository.findSimulationPolicies(
                eq(2026), eq(List.of("1", "ALL")), any(LocalDate.class)))
                .thenReturn(List.of(policy));

        MileageSimulationResponse.Result result = mileageSimulationService.simulate(
                7,
                new MileageSimulationRequest(
                        2026,
                        "1",
                        null,
                        new BigDecimal("400"),
                        List.of(new MileageSimulationRequest.PlannedActivity(21, 1))));

        assertThat(result.target().benefitPolicyId()).isNull();
        assertThat(result.target().targetPoints()).isEqualByComparingTo("400");
        assertThat(result.projectedPoints()).isEqualByComparingTo("400");
        assertThat(result.achieved()).isTrue();
    }

    @Test
    void simulate_rejectsWhenTargetPolicyAndDirectTargetAreBothProvided() {
        assertThatThrownBy(() -> mileageSimulationService.simulate(
                7,
                new MileageSimulationRequest(
                        2026,
                        "1",
                        10,
                        new BigDecimal("500"),
                        List.of())))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    }

    private MileageBenefitPolicy benefitPolicy(
            Integer policyId,
            String benefitType,
            String minimumPoints
    ) {
        MileageBenefitPolicy policy = mock(MileageBenefitPolicy.class);
        when(policy.getBenefitPolicyId()).thenReturn(policyId);
        when(policy.getBenefitType()).thenReturn(benefitType);
        when(policy.getBenefitName()).thenReturn("핵심역량 인증");
        when(policy.getAcademicYear()).thenReturn(2026);
        when(policy.getSemesterCode()).thenReturn("1");
        when(policy.getMinimumPoints()).thenReturn(new BigDecimal(minimumPoints));
        return policy;
    }

    private MileagePolicy mileagePolicy(
            Integer policyId,
            String activityCode,
            String activityName,
            String points,
            String maximumPoints
    ) {
        MileageActivityType activityType = mock(MileageActivityType.class);
        when(activityType.getActivityCode()).thenReturn(activityCode);
        when(activityType.getActivityName()).thenReturn(activityName);

        MileagePolicy policy = mock(MileagePolicy.class);
        when(policy.getMileagePolicyId()).thenReturn(policyId);
        when(policy.getActivityType()).thenReturn(activityType);
        when(policy.getPoints()).thenReturn(new BigDecimal(points));
        when(policy.getMaximumPoints())
                .thenReturn(maximumPoints == null ? null : new BigDecimal(maximumPoints));
        return policy;
    }
}
