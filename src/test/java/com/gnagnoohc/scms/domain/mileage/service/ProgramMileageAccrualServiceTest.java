package com.gnagnoohc.scms.domain.mileage.service;

import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.mileage.entity.MileageActivityType;
import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import com.gnagnoohc.scms.domain.mileage.entity.MileageTransaction;
import com.gnagnoohc.scms.domain.mileage.repository.MileageTransactionRepository;
import com.gnagnoohc.scms.domain.mileage.repository.ProgramApplicationMileageRepository;
import com.gnagnoohc.scms.domain.program.entity.ExtracurricularProgram;
import com.gnagnoohc.scms.domain.program.entity.ProgramApplication;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramMileageAccrualServiceTest {

    @Mock
    private ProgramApplicationMileageRepository programApplicationRepository;
    @Mock
    private MileageTransactionRepository mileageTransactionRepository;

    private ProgramMileageAccrualService programMileageAccrualService;

    @BeforeEach
    void setUp() {
        programMileageAccrualService = new ProgramMileageAccrualService(
                programApplicationRepository,
                mileageTransactionRepository);
    }

    @Test
    void completedProgram_accruesThePolicyPointsOnce() {
        AppUser student = mock(AppUser.class);
        when(student.getUserId()).thenReturn(10);
        MileageActivityType activityType = mock(MileageActivityType.class);
        when(activityType.isActive()).thenReturn(true);
        when(activityType.getCompetency()).thenReturn(mock(Competency.class));
        MileagePolicy policy = applicablePolicy(activityType, "20");
        ExtracurricularProgram program = mock(ExtracurricularProgram.class);
        when(program.getMileagePolicy()).thenReturn(policy);
        ProgramApplication application = mock(ProgramApplication.class);
        when(application.getStudent()).thenReturn(student);
        when(application.getProgram()).thenReturn(program);
        when(programApplicationRepository.findCompletedForAccrual(1))
                .thenReturn(Optional.of(application));
        when(mileageTransactionRepository.findBySourceProgramApplication_ApplicationId(1))
                .thenReturn(Optional.empty());
        when(mileageTransactionRepository.save(any(MileageTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean created = programMileageAccrualService.accrueProgramCompletion(1);

        assertThat(created).isTrue();
        var transactionCaptor = org.mockito.ArgumentCaptor.forClass(MileageTransaction.class);
        verify(mileageTransactionRepository).save(transactionCaptor.capture());
        assertThat(transactionCaptor.getValue().getPoints()).isEqualByComparingTo("20");
        assertThat(transactionCaptor.getValue().getTransactionStatus()).isEqualTo("POSTED");
        assertThat(transactionCaptor.getValue().getTransactionType()).isEqualTo("EARN");
    }

    @Test
    void alreadyAccruedProgram_doesNotCreateAnotherTransaction() {
        when(mileageTransactionRepository.findBySourceProgramApplication_ApplicationId(1))
                .thenReturn(Optional.of(mock(MileageTransaction.class)));

        boolean created = programMileageAccrualService.accrueProgramCompletion(1);

        assertThat(created).isFalse();
        verify(programApplicationRepository, never()).findCompletedForAccrual(any());
        verify(mileageTransactionRepository, never()).save(any());
    }

    @Test
    void completedProgramWithoutApplicablePolicy_isNotAccrued() {
        ProgramApplication application = mock(ProgramApplication.class);
        ExtracurricularProgram program = mock(ExtracurricularProgram.class);
        when(application.getProgram()).thenReturn(program);
        when(program.getMileagePolicy()).thenReturn(null);
        when(programApplicationRepository.findCompletedForAccrual(1))
                .thenReturn(Optional.of(application));
        when(mileageTransactionRepository.findBySourceProgramApplication_ApplicationId(1))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> programMileageAccrualService.accrueProgramCompletion(1))
                .hasMessage("이수일에 적용할 수 있는 마일리지 정책이 없습니다.");
        verify(mileageTransactionRepository, never()).save(any());
    }

    private MileagePolicy applicablePolicy(MileageActivityType activityType, String points) {
        MileagePolicy policy = mock(MileagePolicy.class);
        when(policy.getActivityType()).thenReturn(activityType);
        when(policy.getPoints()).thenReturn(new BigDecimal(points));
        when(policy.getPolicyStatus()).thenReturn("ACTIVE");
        when(policy.isApplicableOn(any(LocalDate.class))).thenReturn(true);
        return policy;
    }
}
