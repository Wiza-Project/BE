package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.MileageScholarshipResponse;
import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitApplication;
import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy;
import com.gnagnoohc.scms.domain.mileage.repository.MileageBenefitApplicationRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageBenefitPolicyRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MileageScholarshipServiceTest {

    @Mock
    private MileageBenefitPolicyRepository benefitPolicyRepository;
    @Mock
    private MileageBenefitApplicationRepository benefitApplicationRepository;
    @Mock
    private MileageTransactionRepository mileageTransactionRepository;
    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private MileageScholarshipService mileageScholarshipService;

    @Test
    void getScholarships_returnsEligibilityAndShortagePoints() {
        MileageBenefitPolicy policy = scholarshipPolicy(10, "1", "100");
        when(benefitPolicyRepository
                .findByActiveTrueAndBenefitTypeAndAcademicYearAndSemesterCodeInOrderByMinimumPointsAsc(
                        eq("SCHOLARSHIP"), eq(2026), eq(List.of("1", "ALL"))))
                .thenReturn(List.of(policy));
        when(benefitApplicationRepository.findApplicationStatuses(7, List.of(10)))
                .thenReturn(List.of());
        when(mileageTransactionRepository.sumPostedPointsByStudentAndPeriod(7, 2026, "1"))
                .thenReturn(new BigDecimal("80"));
        when(mileageTransactionRepository.sumPostedPointsByStudentAndAcademicYear(7, 2026))
                .thenReturn(new BigDecimal("120"));

        List<MileageScholarshipResponse.ScholarshipItem> result =
                mileageScholarshipService.getScholarships(7, 2026, "1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).currentPoints()).isEqualByComparingTo("80");
        assertThat(result.get(0).shortagePoints()).isEqualByComparingTo("20");
        assertThat(result.get(0).eligibilityStatus()).isEqualTo("INSUFFICIENT_POINTS");
        assertThat(result.get(0).canApply()).isFalse();
    }

    @Test
    void apply_savesSnapshotWhenStudentMeetsCriteria() {
        MileageBenefitPolicy policy = scholarshipPolicy(10, "1", "100");
        AppUser student = mock(AppUser.class);
        when(benefitPolicyRepository.findByBenefitPolicyIdAndBenefitTypeAndActiveTrue(10, "SCHOLARSHIP"))
                .thenReturn(Optional.of(policy));
        when(benefitApplicationRepository
                .findByBenefitPolicy_BenefitPolicyIdAndStudent_UserId(10, 7))
                .thenReturn(Optional.empty());
        when(mileageTransactionRepository.sumPostedPointsByStudentAndPeriod(7, 2026, "1"))
                .thenReturn(new BigDecimal("120"));
        when(appUserRepository.findById(7)).thenReturn(Optional.of(student));
        when(benefitApplicationRepository.save(any(MileageBenefitApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MileageScholarshipResponse.ApplicationItem result =
                mileageScholarshipService.apply(7, 10);

        assertThat(result.benefitPolicyId()).isEqualTo(10);
        assertThat(result.pointsSnapshot()).isEqualByComparingTo("120");
        assertThat(result.applicationStatus()).isEqualTo("APPLIED");
        verify(benefitApplicationRepository).save(any(MileageBenefitApplication.class));
    }

    @Test
    void apply_rejectsWhenPointsAreInsufficient() {
        MileageBenefitPolicy policy = scholarshipPolicy(10, "1", "100");
        when(benefitPolicyRepository.findByBenefitPolicyIdAndBenefitTypeAndActiveTrue(10, "SCHOLARSHIP"))
                .thenReturn(Optional.of(policy));
        when(benefitApplicationRepository
                .findByBenefitPolicy_BenefitPolicyIdAndStudent_UserId(10, 7))
                .thenReturn(Optional.empty());
        when(mileageTransactionRepository.sumPostedPointsByStudentAndPeriod(7, 2026, "1"))
                .thenReturn(new BigDecimal("99"));

        assertThatThrownBy(() -> mileageScholarshipService.apply(7, 10))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INSUFFICIENT_MILEAGE);
        verify(appUserRepository, never()).findById(any());
        verify(benefitApplicationRepository, never()).save(any());
    }

    @Test
    void apply_rejectsDuplicateApplication() {
        MileageBenefitPolicy policy = scholarshipPolicy(10, "1", "100");
        MileageBenefitApplication existing = mock(MileageBenefitApplication.class);
        when(benefitPolicyRepository.findByBenefitPolicyIdAndBenefitTypeAndActiveTrue(10, "SCHOLARSHIP"))
                .thenReturn(Optional.of(policy));
        when(benefitApplicationRepository
                .findByBenefitPolicy_BenefitPolicyIdAndStudent_UserId(10, 7))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> mileageScholarshipService.apply(7, 10))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MILEAGE_ALREADY_CLAIMED);
        verify(mileageTransactionRepository, never())
                .sumPostedPointsByStudentAndPeriod(any(), any(), any());
    }

    private MileageBenefitPolicy scholarshipPolicy(
            Integer policyId,
            String semesterCode,
            String minimumPoints
    ) {
        MileageBenefitPolicy policy = mock(MileageBenefitPolicy.class);
        lenient().when(policy.getBenefitPolicyId()).thenReturn(policyId);
        lenient().when(policy.getBenefitType()).thenReturn("SCHOLARSHIP");
        lenient().when(policy.getBenefitName()).thenReturn("마일리지 장학금");
        lenient().when(policy.getAcademicYear()).thenReturn(2026);
        lenient().when(policy.getSemesterCode()).thenReturn(semesterCode);
        lenient().when(policy.getMinimumPoints()).thenReturn(new BigDecimal(minimumPoints));
        lenient().when(policy.getBenefitAmount()).thenReturn(new BigDecimal("500000"));
        return policy;
    }
}
