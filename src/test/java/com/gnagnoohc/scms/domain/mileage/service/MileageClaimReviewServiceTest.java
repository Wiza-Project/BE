package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageClaimCancelRequest;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileageClaimRejectRequest;
import com.gnagnoohc.scms.domain.mileage.entity.ExternalActivityClaim;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;
import com.gnagnoohc.scms.domain.mileage.event.ExternalActivityClaimDecisionEvent;
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
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MileageClaimReviewServiceTest {

    @Mock
    private ExternalActivityClaimRepository externalActivityClaimRepository;
    @Mock
    private MileageTransactionRepository mileageTransactionRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private MileageClaimReviewService mileageClaimReviewService;

    @Test
    void approve_usesPolicyPointsAndCreatesPostedTransaction() {
        ClaimFixture fixture = claimFixture(ExternalActivityClaim.REQUESTED_STATUS);
        lenient().when(fixture.claim().getClaimStatus())
                .thenReturn(ExternalActivityClaim.REQUESTED_STATUS, ExternalActivityClaim.APPROVED_STATUS);
        when(externalActivityClaimRepository.findByIdForUpdate(10))
                .thenReturn(Optional.of(fixture.claim()));
        when(mileageTransactionRepository.findBySourceExternalClaim_ExternalClaimId(10))
                .thenReturn(Optional.empty());
        when(mileageTransactionRepository.save(any(MileageTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = mileageClaimReviewService.approve(10, 99);

        assertThat(result.claimStatus()).isEqualTo(ExternalActivityClaim.APPROVED_STATUS);
        verify(fixture.claim()).approve(any(), any());

        ArgumentCaptor<MileageTransaction> transactionCaptor =
                ArgumentCaptor.forClass(MileageTransaction.class);
        verify(mileageTransactionRepository).save(transactionCaptor.capture());
        MileageTransaction transaction = transactionCaptor.getValue();
        assertThat(transaction.getTransactionType()).isEqualTo("EARN");
        assertThat(transaction.getTransactionStatus()).isEqualTo("POSTED");
        assertThat(transaction.getPoints()).isEqualByComparingTo("10");
        assertThat(transaction.getProcessedBy()).isEqualTo(99);
        assertThat(transaction.getSourceExternalClaim()).isSameAs(fixture.claim());

        ArgumentCaptor<ExternalActivityClaimDecisionEvent> eventCaptor =
                ArgumentCaptor.forClass(ExternalActivityClaimDecisionEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().claimStatus()).isEqualTo(ExternalActivityClaim.APPROVED_STATUS);
        assertThat(eventCaptor.getValue().transactionPoints()).isEqualByComparingTo("10");
    }

    @Test
    void reject_savesReasonWithoutCreatingTransaction() {
        ClaimFixture fixture = claimFixture(ExternalActivityClaim.REQUESTED_STATUS);
        when(externalActivityClaimRepository.findByIdForUpdate(10))
                .thenReturn(Optional.of(fixture.claim()));

        mileageClaimReviewService.reject(
                10, 99, new MileageClaimRejectRequest("증빙 내용이 확인되지 않습니다."));

        verify(fixture.claim()).reject(eq("증빙 내용이 확인되지 않습니다."), eq(99), any());
        verify(mileageTransactionRepository, never()).save(any());
        verify(eventPublisher).publishEvent(any(ExternalActivityClaimDecisionEvent.class));
    }

    @Test
    void cancel_createsNegativeReversalAndPreservesOriginalTransaction() {
        ClaimFixture fixture = claimFixture(ExternalActivityClaim.APPROVED_STATUS);
        lenient().when(fixture.claim().getClaimStatus())
                .thenReturn(ExternalActivityClaim.APPROVED_STATUS, ExternalActivityClaim.CANCELLED_STATUS);
        MileageTransaction original = mock(MileageTransaction.class);
        when(original.getMileageTransactionId()).thenReturn(30);
        when(original.getStudent()).thenReturn(fixture.student());
        when(original.getPoints()).thenReturn(new BigDecimal("10"));
        when(original.getRequestedBy()).thenReturn(7);
        when(mileageTransactionRepository.findBySourceExternalClaim_ExternalClaimId(10))
                .thenReturn(Optional.of(original));
        when(mileageTransactionRepository.findByReversalOfTransaction_MileageTransactionId(30))
                .thenReturn(Optional.empty());
        when(mileageTransactionRepository.save(any(MileageTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(externalActivityClaimRepository.findByIdForUpdate(10))
                .thenReturn(Optional.of(fixture.claim()));

        var result = mileageClaimReviewService.cancel(
                10, 99, new MileageClaimCancelRequest("중복 적립 확인으로 취소"));

        assertThat(result.claimStatus()).isEqualTo(ExternalActivityClaim.CANCELLED_STATUS);
        verify(fixture.claim()).cancel(eq("중복 적립 확인으로 취소"), eq(99), any());

        ArgumentCaptor<MileageTransaction> transactionCaptor =
                ArgumentCaptor.forClass(MileageTransaction.class);
        verify(mileageTransactionRepository).save(transactionCaptor.capture());
        MileageTransaction reversal = transactionCaptor.getValue();
        assertThat(reversal.getTransactionType()).isEqualTo("REVERSE");
        assertThat(reversal.getPoints()).isEqualByComparingTo("-10");
        assertThat(reversal.getReversalOfTransaction()).isSameAs(original);
        verify(eventPublisher).publishEvent(any(ExternalActivityClaimDecisionEvent.class));
    }

    @Test
    void approve_rejectsAlreadyProcessedClaim() {
        ClaimFixture fixture = claimFixture(ExternalActivityClaim.APPROVED_STATUS);
        when(externalActivityClaimRepository.findByIdForUpdate(10))
                .thenReturn(Optional.of(fixture.claim()));

        assertThatThrownBy(() -> mileageClaimReviewService.approve(10, 99))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_ALREADY_PROCESSED);
        verify(mileageTransactionRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void approve_rejectsMissingEvidence() {
        ClaimFixture fixture = claimFixture(ExternalActivityClaim.REQUESTED_STATUS);
        when(fixture.claim().getFileGroup()).thenReturn(null);
        when(externalActivityClaimRepository.findByIdForUpdate(10))
                .thenReturn(Optional.of(fixture.claim()));

        assertThatThrownBy(() -> mileageClaimReviewService.approve(10, 99))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
        verify(mileageTransactionRepository, never()).save(any());
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
        lenient().when(claim.getActivityName()).thenReturn("외부활동 실적");

        lenient().when(student.getUserId()).thenReturn(7);
        lenient().when(activityType.getActivityTypeId()).thenReturn(3);
        lenient().when(activityType.isActive()).thenReturn(true);
        lenient().when(activityType.getCompetency()).thenReturn(mock(com.gnagnoohc.scms.domain.competency.entity.Competency.class));
        lenient().when(policy.getActivityType()).thenReturn(activityType);
        lenient().when(policy.getPolicyStatus()).thenReturn("ACTIVE");
        lenient().when(policy.getValidFrom()).thenReturn(LocalDate.of(2026, 1, 1));
        lenient().when(policy.getValidTo()).thenReturn(LocalDate.of(2026, 12, 31));
        lenient().when(policy.getPoints()).thenReturn(new BigDecimal("10"));
        lenient().when(policy.getMaximumPoints()).thenReturn(new BigDecimal("20"));

        return new ClaimFixture(claim, student);
    }

    private record ClaimFixture(ExternalActivityClaim claim, AppUser student) {
    }
}
