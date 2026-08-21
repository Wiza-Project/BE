package com.gnagnoohc.scms.domain.program.scheduler;

import com.gnagnoohc.scms.domain.program.event.ProgramCompletionJudgedEvent;
import com.gnagnoohc.scms.domain.program.repository.ExtracurricularProgramRepository;
import com.gnagnoohc.scms.domain.program.repository.ProgramApplicationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProgramStatusSchedulerTest {

    @Mock
    ExtracurricularProgramRepository programRepository;

    @Mock
    ProgramApplicationRepository applicationRepository;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    ProgramStatusScheduler programStatusScheduler;

    @Test
    void transitionProgramStatuses_callsStepsInOrder_recruitingToOperating_operatingToClosed_judgeCompletion() {
        when(programRepository.transitionRecruitingToOperating(any(Instant.class))).thenReturn(2);
        when(programRepository.transitionOperatingToClosed(any(Instant.class))).thenReturn(1);
        when(applicationRepository.judgeCompletion(any(Instant.class))).thenReturn(3);
        when(applicationRepository.findApplicationIdsJudgedCompletedAt(any(Instant.class))).thenReturn(List.of());

        programStatusScheduler.transitionProgramStatuses();

        InOrder order = inOrder(programRepository, applicationRepository);
        order.verify(programRepository).transitionRecruitingToOperating(any(Instant.class));
        order.verify(programRepository).transitionOperatingToClosed(any(Instant.class));
        order.verify(applicationRepository).judgeCompletion(any(Instant.class));
        order.verify(applicationRepository).findApplicationIdsJudgedCompletedAt(any(Instant.class));
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

    @Test
    void completionJudged_zero_skipsLookupAndPublishesNoEvent() {
        when(programRepository.transitionRecruitingToOperating(any(Instant.class))).thenReturn(0);
        when(programRepository.transitionOperatingToClosed(any(Instant.class))).thenReturn(0);
        when(applicationRepository.judgeCompletion(any(Instant.class))).thenReturn(0);

        programStatusScheduler.transitionProgramStatuses();

        verify(applicationRepository, never()).findApplicationIdsJudgedCompletedAt(any(Instant.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void completionJudged_positive_publishesEventPerJudgedApplicationId() {
        when(programRepository.transitionRecruitingToOperating(any(Instant.class))).thenReturn(0);
        when(programRepository.transitionOperatingToClosed(any(Instant.class))).thenReturn(0);
        when(applicationRepository.judgeCompletion(any(Instant.class))).thenReturn(2);
        when(applicationRepository.findApplicationIdsJudgedCompletedAt(any(Instant.class)))
                .thenReturn(List.of(11, 22));

        programStatusScheduler.transitionProgramStatuses();

        ArgumentCaptor<ProgramCompletionJudgedEvent> captor =
                ArgumentCaptor.forClass(ProgramCompletionJudgedEvent.class);
        verify(eventPublisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ProgramCompletionJudgedEvent::applicationId)
                .containsExactly(11, 22);
    }
}
