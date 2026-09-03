package com.gnagnoohc.scms.domain.career.listener;

import com.gnagnoohc.scms.domain.program.event.ExtracurricularActivityCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * program 도메인의 비교과 이수 확정 이벤트를 받아 {@link ResumeExtracurricularActivityUpsertService}에
 * 저장을 위임한다.
 *
 * <p>{@code ExtracurricularActivityCompletedEvent} 문서에 명시된 대로 원천 트랜잭션 커밋 이후에만
 * 반영돼야 하므로 {@code AFTER_COMMIT}으로 구독한다.</p>
 *
 * <p>{@code ResumeCompetencySnapshotEventListener}와 달리 재시도는 하지 않는다 — 여기서 다루는
 * 유니크 제약 위반은 같은 신청 건의 이벤트가 중복 전달된 경우뿐이고, 두 번째 이벤트가 담은 데이터는
 * 첫 번째와 완전히 동일해 반영할 게 더 없으므로 조용히 건너뛴다({@link
 * ResumeExtracurricularActivityUpsertService} 문서 참고).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeExtracurricularActivityEventListener {

    private final ResumeExtracurricularActivityUpsertService resumeExtracurricularActivityUpsertService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ExtracurricularActivityCompletedEvent event) {
        try {
            resumeExtracurricularActivityUpsertService.save(event);
        } catch (DataIntegrityViolationException e) {
            // existsByApplicationId 선확인과 저장 사이의 짧은 경합 — 이겼든 졌든 결과 데이터는 동일하므로 건너뛴다.
            log.info("이력서 비교과 이력 저장 충돌(중복 이벤트로 판단) — applicationId={}", event.applicationId());
        }
    }
}
