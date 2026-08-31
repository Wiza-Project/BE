package com.gnagnoohc.scms.domain.competency.listener;

import com.gnagnoohc.scms.domain.career.event.ResumeCompetencySyncRequestedEvent;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.event.AssessmentResultUnavailableEvent;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.competency.service.AssessmentResultReadyEventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeCompetencySyncRequestedEventListenerTest {

    private static final Integer STUDENT_ID = 7;
    private static final Integer ATTEMPT_ID = 42;

    @Mock
    AssessmentAttemptRepository assessmentAttemptRepository;

    @Mock
    AssessmentResultReadyEventPublisher assessmentResultReadyEventPublisher;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @InjectMocks
    ResumeCompetencySyncRequestedEventListener listener;

    private static AssessmentAttempt attemptWithId(Integer id) throws ReflectiveOperationException {
        Constructor<AssessmentAttempt> constructor = AssessmentAttempt.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        AssessmentAttempt attempt = constructor.newInstance();
        ReflectionTestUtils.setField(attempt, "attemptId", id);
        return attempt;
    }

    @Test
    void handle_whenCompletedAttemptExists_republishesReadyEventWithRequestId() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(assessmentAttemptRepository
                .findFirstByStudent_UserIdAndSubmittedAtIsNotNullOrderBySubmittedAtDescAttemptIdDesc(STUDENT_ID))
                .thenReturn(Optional.of(attemptWithId(ATTEMPT_ID)));
        when(assessmentResultReadyEventPublisher.publish(ATTEMPT_ID, requestId)).thenReturn(true);

        listener.handle(new ResumeCompetencySyncRequestedEvent(STUDENT_ID, requestId, Instant.now()));

        verify(assessmentResultReadyEventPublisher).publish(ATTEMPT_ID, requestId);
        verify(eventPublisher, never()).publishEvent(any());
    }

    // 완료 attempt는 있으나 환산점수가 없어 결과 준비 이벤트가 발행되지 않은 경우(publish=false) —
    // 요청이 종료되도록 결과 없음 이벤트로 대체해야 한다.
    @Test
    void handle_whenAttemptExistsButReadyEventNotPublished_fallsBackToUnavailableEvent() throws Exception {
        UUID requestId = UUID.randomUUID();
        when(assessmentAttemptRepository
                .findFirstByStudent_UserIdAndSubmittedAtIsNotNullOrderBySubmittedAtDescAttemptIdDesc(STUDENT_ID))
                .thenReturn(Optional.of(attemptWithId(ATTEMPT_ID)));
        when(assessmentResultReadyEventPublisher.publish(ATTEMPT_ID, requestId)).thenReturn(false);

        listener.handle(new ResumeCompetencySyncRequestedEvent(STUDENT_ID, requestId, Instant.now()));

        ArgumentCaptor<AssessmentResultUnavailableEvent> captor =
                ArgumentCaptor.forClass(AssessmentResultUnavailableEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().requestId()).isEqualTo(requestId);
        assertThat(captor.getValue().reason())
                .isEqualTo(AssessmentResultUnavailableEvent.REASON_NO_COMPLETED_ASSESSMENT);
    }

    @Test
    void handle_whenNoCompletedAttempt_publishesUnavailableEventWithReason() {
        UUID requestId = UUID.randomUUID();
        when(assessmentAttemptRepository
                .findFirstByStudent_UserIdAndSubmittedAtIsNotNullOrderBySubmittedAtDescAttemptIdDesc(STUDENT_ID))
                .thenReturn(Optional.empty());

        listener.handle(new ResumeCompetencySyncRequestedEvent(STUDENT_ID, requestId, Instant.now()));

        ArgumentCaptor<AssessmentResultUnavailableEvent> captor =
                ArgumentCaptor.forClass(AssessmentResultUnavailableEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        AssessmentResultUnavailableEvent event = captor.getValue();
        assertThat(event.studentId()).isEqualTo(STUDENT_ID);
        assertThat(event.requestId()).isEqualTo(requestId);
        assertThat(event.reason()).isEqualTo(AssessmentResultUnavailableEvent.REASON_NO_COMPLETED_ASSESSMENT);
        assertThat(event.occurredAt()).isNotNull();
        verify(assessmentResultReadyEventPublisher, never()).publish(any(), any());
    }
}
