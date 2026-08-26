package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageClaimApproveRequest;
import com.gnagnoohc.scms.domain.mileage.DTO.response.MileageClaimReviewResultResponse;
import com.gnagnoohc.scms.domain.mileage.entity.ExternalActivityClaim;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;
import com.gnagnoohc.scms.domain.mileage.repository.ExternalActivityClaimRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.FileGroup;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MileageClaimReviewServiceTest {

    @Mock
    private ExternalActivityClaimRepository externalActivityClaimRepository;
    @Mock
    private MileageTransactionRepository mileageTransactionRepository;

    @InjectMocks
    private MileageClaimReviewService mileageClaimReviewService;

    @Test
    void approve_createsPostedExternalClaimTransaction() {
        ClaimFixture fixture = claimFixture("REQUESTED");
        when(externalActivityClaimRepository.findByIdForUpdate(10))
                .thenReturn(Optional.of(fixture.claim()));
        when(mileageTransactionRepository.findBySourceExternalClaim_ExternalClaimId(10))
                .thenReturn(Optional.empty());
        when(mileageTransactionRepository.save(any(MileageTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MileageClaimReviewResultResponse result = mileageClaimReviewService.approve(
                10, 99, new MileageClaimApproveRequest(null));

        assertThat(result.externalClaimId()).isEqualTo(10);
        verify(fixture.claim()).approve(any(), any());

        ArgumentCaptor<MileageTransaction> transactionCaptor =
                ArgumentCaptor.forClass(MileageTransaction.class);
        verify(mileageTransactionRepository).save(transactionCaptor.capture());
        MileageTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getTransactionType()).isEqualTo("EARN");
        assertThat(transaction.getTransactionStatus()).isEqualTo("POSTED");
        assertThat(transaction.getPoints()).isEqualByComparingTo("8");
        assertThat(transaction.getProcessedBy()).isEqualTo(99);
        assertThat(transaction.getSourceExternalClaim()).isSameAs(fixture.claim());
    }

    @Test
    void reject_savesReasonWithoutCreatingTransaction() {
        ClaimFixture fixture = claimFixture("REQUESTED");
        when(externalActivityClaimRepository.findByIdForUpdate(10))
                .thenReturn(Optional.of(fixture.claim()));

        mileageClaimReviewService.reject(10, 99, "증빙 내용이 확인되지 않습니다.");

        verify(fixture.claim()).reject(
                eq("증빙 내용이 확인되지 않습니다."), eq(99), any());
        verify(mileageTransactionRepository, never()).save(any());
    }

    @Test
    void approve_rejectsAlreadyProcessedClaim() {
        ClaimFixture fixture = claimFixture("APPROVED");
        when(externalActivityClaimRepository.findByIdForUpdate(10))
                .thenReturn(Optional.of(fixture.claim()));

        assertThatThrownBy(() -> mileageClaimReviewService.approve(
                10, 99, new MileageClaimApproveRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_ALREADY_PROCESSED);
        verify(mileageTransactionRepository, never())
                .findBySourceExternalClaim_ExternalClaimId(any());
        verify(mileageTransactionRepository, never()).save(any());
    }

    @Test
    void approve_rejectsDuplicateTransaction() {
        ClaimFixture fixture = claimFixture("REQUESTED");
        when(externalActivityClaimRepository.findByIdForUpdate(10))
                .thenReturn(Optional.of(fixture.claim()));
        when(mileageTransactionRepository.findBySourceExternalClaim_ExternalClaimId(10))
                .thenReturn(Optional.of(mock(MileageTransaction.class)));

        assertThatThrownBy(() -> mileageClaimReviewService.approve(
                10, 99, new MileageClaimApproveRequest(null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_ALREADY_PROCESSED);
        verify(fixture.claim(), never()).approve(any(), any());
        verify(mileageTransactionRepository, never()).save(any());
    }

    @Test
    void reject_requiresReason() {
        assertThatThrownBy(() -> mileageClaimReviewService.reject(10, 99, " "))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
        verify(externalActivityClaimRepository, never()).findByIdForUpdate(any());
    }

    private ClaimFixture claimFixture(String status) {
        ExternalActivityClaim claim = mock(ExternalActivityClaim.class);
        AppUser student = mock(AppUser.class);
        FileGroup fileGroup = mock(FileGroup.class);
        MileageActivityType activityType = mock(MileageActivityType.class);
        MileagePolicy policy = mock(MileagePolicy.class);

        lenient().when(claim.getExternalClaimId()).thenReturn(10);
        lenient().when(claim.getClaimStatus()).thenReturn(status);
        lenient().when(claim.getStudent()).thenReturn(student);
        lenient().when(claim.getFileGroup()).thenReturn(fileGroup);
        lenient().when(claim.getActivityType()).thenReturn(activityType);
        lenient().when(claim.getMileagePolicy()).thenReturn(policy);
        lenient().when(claim.getActivityDate()).thenReturn(LocalDate.of(2026, 8, 25));
        lenient().when(claim.getRequestedPoints()).thenReturn(new BigDecimal("8"));

        lenient().when(student.getUserId()).thenReturn(7);
        lenient().when(activityType.getActivityTypeId()).thenReturn(3);
        lenient().when(activityType.isActive()).thenReturn(true);
        lenient().when(policy.getActivityType()).thenReturn(activityType);
        lenient().when(policy.getPolicyStatus()).thenReturn("ACTIVE");
        lenient().when(policy.getValidFrom()).thenReturn(LocalDate.of(2026, 1, 1));
        lenient().when(policy.getValidTo()).thenReturn(LocalDate.of(2026, 12, 31));
        lenient().when(policy.getPoints()).thenReturn(new BigDecimal("10"));
        lenient().when(policy.getMaximumPoints()).thenReturn(new BigDecimal("20"));
        lenient().when(activityType.getCompetency())
                .thenReturn(mock(com.gnagnoohc.scms.domain.competency.entity.Competency.class));

        return new ClaimFixture(claim);
    }

    private record ClaimFixture(ExternalActivityClaim claim) {
    }
}
