package com.gnagnoohc.scms.domain.career.listener;

import com.gnagnoohc.scms.domain.career.entity.ResumeCompetencySnapshot;
import com.gnagnoohc.scms.domain.competency.event.AssessmentResultReadyEvent;
import com.gnagnoohc.scms.domain.competency.event.AssessmentResultUnavailableEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 핵심역량 도메인의 이력서 연동 이벤트를 받아 {@link ResumeCompetencySnapshotUpsertService}에 upsert를 위임한다.
 *
 * <p>두 이벤트 모두 핵심역량 쪽 자체 트랜잭션 안에서 발행된다({@code AssessmentResultReadyEvent},
 * {@code AssessmentResultUnavailableEvent} 문서 참고) — 그래서 {@code AFTER_COMMIT}으로 구독한다.</p>
 *
 * <p><strong>동시 최초 생성 경합</strong>: {@link ResumeCompetencySnapshot}은 학생당 1행만 허용하는
 * {@code student_id} 유니크 제약이 있다. 같은 학생이 스냅샷이 아직 없는 상태에서 재연동을 거의 동시에
 * 두 번 요청하면, 두 요청 모두 "행 없음"을 보고 새로 만들려다 하나는 유니크 제약 위반으로 실패할 수 있다.
 * 이 실패는 진 쪽 트랜잭션이 완전히 롤백된 뒤에만 발생하고(그래야 제약 위반이 나므로), 그 시점엔 이긴 쪽이
 * 이미 커밋을 마친 상태다 — 그래서 재시도 시 재조회하면 반드시 그 행을 찾아 UPDATE로 성공한다. 최대 2회만
 * 시도하는 이유도 이 보장 때문이다(3번째 경쟁자가 끼어들어도 같은 논리로 2회째에 반드시 성공한다).</p>
 *
 * <p>이 재시도가 왜 리스너 메서드 안에 있어야 하는가: {@code AFTER_COMMIT}에서 예외가 나면 Spring은 그걸
 * 호출부(재연동 요청 스레드)로 올려보내지 않고 {@code TransactionSynchronization.afterCompletion} 단계에서
 * 로그만 남기고 삼킨다({@code ResumeCompetencyIntegrationTest}에서 직접 확인). 즉 여기서 재시도하지 않으면
 * 사용자는 에러조차 못 보고 방금 만든 결과가 조용히 유실된다 — 요청 실패보다 위험한 실패 양상이다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeCompetencySnapshotEventListener {

    private static final int MAX_ATTEMPTS = 2;

    private final ResumeCompetencySnapshotUpsertService resumeCompetencySnapshotUpsertService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReady(AssessmentResultReadyEvent event) {
        upsertWithRetry(event.studentId(), () -> resumeCompetencySnapshotUpsertService.applyReady(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUnavailable(AssessmentResultUnavailableEvent event) {
        upsertWithRetry(event.studentId(), () -> resumeCompetencySnapshotUpsertService.applyUnavailable(event));
    }

    private void upsertWithRetry(Integer studentId, Runnable upsert) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                upsert.run();
                return;
            } catch (DataIntegrityViolationException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                log.info("이력서 핵심역량 스냅샷 upsert 충돌 — 재조회 후 재시도 (studentId={}, attempt={})", studentId, attempt);
            }
        }
    }
}
