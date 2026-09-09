package com.gnagnoohc.scms.domain.mileage.listener;

import com.gnagnoohc.scms.domain.mileage.service.ProgramMileageAccrualService;
import com.gnagnoohc.scms.domain.program.event.ProgramCompletionJudgedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 비교과 이수 확정 이벤트를 받아 마일리지 원장 적립을 수행한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProgramCompletionJudgedEventListener {

    private final ProgramMileageAccrualService programMileageAccrualService;

    /**
     * 발행부({@code ProgramStatusScheduler}의 이수 판정) 트랜잭션이 커밋된 뒤 AFTER_COMMIT으로 처리한다.
     * 이수 판정과 이수번호(certificate_no) 채번은 {@code judgeCompletion} UPDATE 안에서 원자적으로
     * 끝나므로, 적립을 같은 트랜잭션에 묶지 않아도 판정·이수증 발급의 원자성은 유지된다. 적립은 부가
     * 기능이므로 실패하더라도 판정을 롤백하지 않는다.
     *
     * <p>적립은 최선 노력(best-effort)이다. AFTER_COMMIT 단계에서 나는 예외는 Spring이 삼키므로 여기서
     * 직접 잡아 에러 로그로 남긴다. 누락분은 {@code ProgramMileageAccrualService.accruePendingProgramCompletions}
     * 스케줄러 배치가 다음 사이클에 회수한다.</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProgramCompletionJudgedEvent event) {
        try {
            programMileageAccrualService.accrueProgramCompletion(event.applicationId());
        } catch (RuntimeException e) {
            log.error("비교과 이수 완료 마일리지 적립 실패 — applicationId={}", event.applicationId(), e);
        }
    }
}
