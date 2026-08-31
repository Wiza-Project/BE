package com.gnagnoohc.scms.domain.competency.support;

import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.competency.service.AssessmentResultReadyEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentResultBackfillRunnerTest {

    @Mock
    AssessmentAttemptRepository assessmentAttemptRepository;

    @Mock
    AssessmentResultReadyEventPublisher assessmentResultReadyEventPublisher;

    @InjectMocks
    AssessmentResultBackfillRunner runner;

    @Test
    void run_publishesReadyEventForEverySubmittedAttempt_withNullRequestId() {
        when(assessmentAttemptRepository.findSubmittedAttemptIds(any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(1, 2, 3), Pageable.ofSize(200), false));

        runner.run();

        verify(assessmentResultReadyEventPublisher).publish(eq(1), isNull());
        verify(assessmentResultReadyEventPublisher).publish(eq(2), isNull());
        verify(assessmentResultReadyEventPublisher).publish(eq(3), isNull());
    }

    @Test
    void run_isSafeToRunRepeatedly_andSkipsFailingAttempts() {
        when(assessmentAttemptRepository.findSubmittedAttemptIds(any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(1, 2, 3), Pageable.ofSize(200), false));
        // 한 건이 실패해도 나머지는 계속 발행되어야 하고, 전체가 예외를 던지지 않아야 한다.
        doThrow(new RuntimeException("boom")).when(assessmentResultReadyEventPublisher).publish(eq(2), isNull());

        runner.run();
        runner.run(); // 반복 실행해도 예외 없이 동일하게 동작

        verify(assessmentResultReadyEventPublisher, times(2)).publish(eq(1), isNull());
        verify(assessmentResultReadyEventPublisher, times(2)).publish(eq(2), isNull());
        verify(assessmentResultReadyEventPublisher, times(2)).publish(eq(3), isNull());
    }
}
