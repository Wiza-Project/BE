package com.gnagnoohc.scms.domain.program.scheduler;

import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramStatusSchedulerTest {

    @Mock
    ExtracurricularProgramRepository programRepository;

    @Mock
    ProgramApplicationRepository applicationRepository;

    @InjectMocks
    ProgramStatusScheduler programStatusScheduler;

    @Test
    void transitionProgramStatuses_callsStepsInOrder_recruitingToOperating_operatingToClosed_judgeCompletion() {
        when(programRepository.transitionRecruitingToOperating(any(Instant.class))).thenReturn(2);
        when(programRepository.transitionOperatingToClosed(any(Instant.class))).thenReturn(1);
        when(applicationRepository.judgeCompletion(any(Instant.class))).thenReturn(3);

        programStatusScheduler.transitionProgramStatuses();

        InOrder order = inOrder(programRepository, applicationRepository);
        order.verify(programRepository).transitionRecruitingToOperating(any(Instant.class));
        order.verify(programRepository).transitionOperatingToClosed(any(Instant.class));
        order.verify(applicationRepository).judgeCompletion(any(Instant.class));
    }

    @Test
    void transitionProgramStatuses_invokesAllRepositoryMethodsExactlyOnce() {
        when(programRepository.transitionRecruitingToOperating(any(Instant.class))).thenReturn(0);
        when(programRepository.transitionOperatingToClosed(any(Instant.class))).thenReturn(0);
        when(applicationRepository.judgeCompletion(any(Instant.class))).thenReturn(0);

        programStatusScheduler.transitionProgramStatuses();

        verify(programRepository, times(1)).transitionRecruitingToOperating(any(Instant.class));
        verify(programRepository, times(1)).transitionOperatingToClosed(any(Instant.class));
        verify(applicationRepository, times(1)).judgeCompletion(any(Instant.class));
    }
}
