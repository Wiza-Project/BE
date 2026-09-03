package com.gnagnoohc.scms.domain.competency.listener;

import com.gnagnoohc.scms.domain.career.event.ResumeCompetencySyncRequestedEvent;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.event.AssessmentResultUnavailableEvent;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.competency.service.AssessmentResultReadyEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * 취창업의 이력서 재연동 요청({@link ResumeCompetencySyncRequestedEvent})을 받아,
 * 학생의 완료 진단 최신 1건이 있으면 결과 준비 이벤트를, 없으면 결과 없음 이벤트를 requestId를 달아 재발행한다.
 *
 * <p>재연동 요청은 반드시 두 이벤트 중 하나로 끝맺어야 한다(그렇지 않으면 취창업 화면이 "결과 없음"과
 * "처리 지연·실패"를 구분하지 못한다). 완료 attempt를 찾았더라도 환산점수가 없어 결과 준비 이벤트가
 * 발행되지 않으면, 이 리스너가 결과 없음 이벤트로 대체해 요청을 종료시킨다.</p>
 */
@Component
@RequiredArgsConstructor
public class ResumeCompetencySyncRequestedEventListener {

    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final AssessmentResultReadyEventPublisher assessmentResultReadyEventPublisher;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 기본 동기 {@code @EventListener} + 자체 트랜잭션으로 처리한다.
     *
     * <p>이 요청 이벤트는 취창업이 이력서 화면 상호작용에서, 자신의 트랜잭션이 커밋된 뒤(또는 비트랜잭션
     * 컨텍스트에서) 발행한다는 전제다. 그래서 여기에 붙일 취창업 트랜잭션이 없고,
     * {@code @TransactionalEventListener(AFTER_COMMIT)}로 두면 바인딩된 트랜잭션이 없어 핸들러가
     * 조용히 실행되지 않을 수 있다. 대신 기본 리스너로 받고, attempt·환산점수 지연 로딩과 후속 이벤트
     * 발행을 담을 트랜잭션을 리스너가 직접 연다. 재발행되는 결과 준비 이벤트는 이 트랜잭션 커밋 후
     * 취창업이 다시 AFTER_COMMIT으로 구독한다.</p>
     */
    @EventListener
    @Transactional
    public void handle(ResumeCompetencySyncRequestedEvent event) {
        Optional<AssessmentAttempt> latestCompleted = assessmentAttemptRepository
                .findFirstByStudent_UserIdAndSubmittedAtIsNotNullOrderBySubmittedAtDescAttemptIdDesc(event.studentId());

        boolean readyEventPublished = latestCompleted.isPresent()
                && assessmentResultReadyEventPublisher.publish(latestCompleted.get().getAttemptId(), event.requestId());

        // 완료 attempt가 없거나, 있어도 환산점수가 없어 결과 준비 이벤트가 발행되지 않은 경우 —
        // 취창업이 무한 대기하지 않도록 결과 없음 이벤트로 요청을 종료시킨다.
        if (!readyEventPublished) {
            eventPublisher.publishEvent(new AssessmentResultUnavailableEvent(
                    event.studentId(),
                    event.requestId(),
                    AssessmentResultUnavailableEvent.REASON_NO_COMPLETED_ASSESSMENT,
                    Instant.now()));
        }
    }
}
