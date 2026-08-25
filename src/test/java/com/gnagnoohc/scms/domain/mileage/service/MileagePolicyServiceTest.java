package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.mileage.DTO.request.MileagePolicyRegisterRequestDTO;
import com.gnagnoohc.scms.domain.mileage.DTO.request.MileagePolicyUpdateRequestDTO;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.repository.MileageActivityTypeRepository;
import com.gnagnoohc.scms.domain.mileage.repository.MileagePolicyRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MileagePolicyServiceTest {

    @Mock
    private MileagePolicyRepository policyRepository;
    @Mock
    private MileageActivityTypeRepository activityTypeRepository;

    @InjectMocks
    private MileagePolicyService mileagePolicyService;

    @Test
    void register_autoAssignsNextVersionNo() {
        MileageActivityType activityType = mock(MileageActivityType.class);
        when(activityType.isActive()).thenReturn(true);
        when(activityType.getActivityTypeId()).thenReturn(5);
        when(activityTypeRepository.findByIdForUpdate(5)).thenReturn(Optional.of(activityType));
        when(policyRepository.findNextVersionNo(5, 2026, "ALL")).thenReturn(2);
        when(policyRepository.insertPolicy(eq(5), eq(2026), eq("ALL"), eq(2), any(), any(), any(), any(), any(), eq("ACTIVE"), eq(100), any()))
                .thenReturn(99);
        MileagePolicy saved = mock(MileagePolicy.class);
        when(saved.getActivityType()).thenReturn(activityType);
        when(policyRepository.findById(99)).thenReturn(Optional.of(saved));

        MileagePolicyRegisterRequestDTO request = new MileagePolicyRegisterRequestDTO(
                5, 2026, null, new BigDecimal("10"), null, LocalDate.of(2026, 3, 1), null, null);

        mileagePolicyService.register(request, 100);

        var versionCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(policyRepository).insertPolicy(eq(5), eq(2026), eq("ALL"), versionCaptor.capture(),
                any(), any(), any(), any(), any(), eq("ACTIVE"), eq(100), any());
        assertThat(versionCaptor.getValue()).isEqualTo(2);
    }

    @Test
    void register_throwsWhenActivityTypeInactive() {
        MileageActivityType activityType = mock(MileageActivityType.class);
        when(activityType.isActive()).thenReturn(false);
        when(activityTypeRepository.findByIdForUpdate(5)).thenReturn(Optional.of(activityType));

        MileagePolicyRegisterRequestDTO request = new MileagePolicyRegisterRequestDTO(
                5, 2026, null, new BigDecimal("10"), null, LocalDate.of(2026, 3, 1), null, null);

        assertThatThrownBy(() -> mileagePolicyService.register(request, 100))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MILEAGE_ACTIVITY_TYPE_NOT_FOUND);
        verify(policyRepository, never()).insertPolicy(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void register_throwsWhenValidFromNotBeforeValidTo() {
        MileageActivityType activityType = mock(MileageActivityType.class);
        when(activityType.isActive()).thenReturn(true);
        when(activityTypeRepository.findByIdForUpdate(5)).thenReturn(Optional.of(activityType));

        MileagePolicyRegisterRequestDTO request = new MileagePolicyRegisterRequestDTO(
                5, 2026, null, new BigDecimal("10"), null,
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1), null);

        assertThatThrownBy(() -> mileagePolicyService.register(request, 100))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MILEAGE_POLICY_INVALID_PERIOD);
    }

    @Test
    void register_whenUniqueConstraintViolated_throwsMileagePolicyDuplicate() {
        MileageActivityType activityType = mock(MileageActivityType.class);
        when(activityType.isActive()).thenReturn(true);
        when(activityType.getActivityTypeId()).thenReturn(5);
        when(activityTypeRepository.findByIdForUpdate(5)).thenReturn(Optional.of(activityType));
        when(policyRepository.findNextVersionNo(5, 2026, "ALL")).thenReturn(1);
        when(policyRepository.insertPolicy(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_mileage_policy_activity_period_version\""));

        MileagePolicyRegisterRequestDTO request = new MileagePolicyRegisterRequestDTO(
                5, 2026, null, new BigDecimal("10"), null, LocalDate.of(2026, 3, 1), null, null);

        assertThatThrownBy(() -> mileagePolicyService.register(request, 100))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MILEAGE_POLICY_DUPLICATE);
    }

    @Test
    void update_mergesNullFieldsWithExistingValues() {
        MileageActivityType activityType = mock(MileageActivityType.class);
        MileagePolicy existing = mock(MileagePolicy.class);
        when(existing.getActivityType()).thenReturn(activityType);
        when(existing.getPoints()).thenReturn(new BigDecimal("10"));
        when(existing.getMaximumPoints()).thenReturn(null);
        when(existing.getValidFrom()).thenReturn(LocalDate.of(2026, 3, 1));
        when(existing.getValidTo()).thenReturn(null);
        when(existing.getDuplicateRule()).thenReturn(null);
        when(existing.getPolicyStatus()).thenReturn("ACTIVE");
        when(policyRepository.findByIdForUpdate(99)).thenReturn(Optional.of(existing));
        when(policyRepository.findById(99)).thenReturn(Optional.of(existing));
        when(policyRepository.updatePolicy(eq(99), eq(new BigDecimal("10")), eq(null),
                eq(LocalDate.of(2026, 3, 1)), eq(null), eq(null), eq("INACTIVE")))
                .thenReturn(1);

        MileagePolicyUpdateRequestDTO request = new MileagePolicyUpdateRequestDTO(
                null, null, null, null, false, null, "INACTIVE");

        mileagePolicyService.update(99, request);

        verify(policyRepository).updatePolicy(99, new BigDecimal("10"), null,
                LocalDate.of(2026, 3, 1), null, null, "INACTIVE");
    }

    @Test
    void update_whenNoRowsUpdated_throwsNotFound() {
        // updatePolicy가 0행을 반환해 예외로 끝나므로, getDetail()까지 도달하지 않아 getActivityType()은 스텁하지 않는다.
        MileagePolicy existing = mock(MileagePolicy.class);
        when(existing.getPoints()).thenReturn(new BigDecimal("10"));
        when(existing.getValidFrom()).thenReturn(LocalDate.of(2026, 3, 1));
        when(existing.getPolicyStatus()).thenReturn("ACTIVE");
        when(policyRepository.findByIdForUpdate(99)).thenReturn(Optional.of(existing));
        when(policyRepository.updatePolicy(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        MileagePolicyUpdateRequestDTO request = new MileagePolicyUpdateRequestDTO(
                null, null, null, null, false, null, null);

        assertThatThrownBy(() -> mileagePolicyService.update(99, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MILEAGE_POLICY_NOT_FOUND);
    }

    @Test
    void update_whenClearValidToTrue_setsValidToNull() {
        MileagePolicy existing = mock(MileagePolicy.class);
        when(existing.getActivityType()).thenReturn(mock(MileageActivityType.class));
        when(existing.getPoints()).thenReturn(new BigDecimal("10"));
        when(existing.getMaximumPoints()).thenReturn(null);
        when(existing.getValidFrom()).thenReturn(LocalDate.of(2026, 3, 1));
        when(existing.getDuplicateRule()).thenReturn(null);
        when(existing.getPolicyStatus()).thenReturn("ACTIVE");
        when(policyRepository.findByIdForUpdate(99)).thenReturn(Optional.of(existing));
        when(policyRepository.findById(99)).thenReturn(Optional.of(existing));
        when(policyRepository.updatePolicy(eq(99), any(), any(), any(), eq(null), any(), any()))
                .thenReturn(1);

        MileagePolicyUpdateRequestDTO request = new MileagePolicyUpdateRequestDTO(
                null, null, null, null, true, null, null);

        mileagePolicyService.update(99, request);

        verify(policyRepository).updatePolicy(eq(99), any(), any(), any(), eq(null), any(), any());
    }

    @Test
    void update_whenValidToAndClearValidToBothSet_throwsConflict() {
        MileagePolicyUpdateRequestDTO request = new MileagePolicyUpdateRequestDTO(
                null, null, null, LocalDate.of(2026, 8, 31), true, null, null);

        assertThatThrownBy(() -> mileagePolicyService.update(99, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MILEAGE_POLICY_VALID_TO_CONFLICT);
        verify(policyRepository, never()).findByIdForUpdate(any());
    }
}
