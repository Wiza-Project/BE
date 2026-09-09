package com.gnagnoohc.scms.domain.mileage.listener;

import com.gnagnoohc.scms.domain.competency.event.AssessmentResultReadyEvent;
import com.gnagnoohc.scms.domain.mileage.service.CompetencyDiagnosisMileageAccrualService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 역량진단 결과 준비 이벤트를 받아 마일리지 원장 적립을 수행한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssessmentResultReadyMileageEventListener {

    private final CompetencyDiagnosisMileageAccrualService competencyDiagnosisMileageAccrualService;

    /**
     * 발행부(역량진단 제출) 트랜잭션이 커밋된 뒤 AFTER_COMMIT으로 처리한다. 적립은 부가 기능이므로
     * 실패하더라도 제출을 롤백하지 않는다. 같은 {@link AssessmentResultReadyEvent}를 구독하는 취창업
     * 리스너({@code ResumeCompetencySnapshotEventListener})도 동일하게 AFTER_COMMIT을 쓴다.
     *
     * <p>적립은 최선 노력(best-effort)이다. {@code accrueAssessmentCompletion}은 정책 없음·한도 초과·
     * 역량 미연결 등 대부분의 실패를 {@code return false} + 로그로 흡수하고, 그 밖의 예외(응시 미조회,
     * 정책 설정 오류 등)는 AFTER_COMMIT 단계라 Spring이 삼키므로 여기서 직접 잡아 에러 로그로 남긴다.
     * 누락분은 역량진단 재연동/백필 재발행 시 멱등 재시도로 회수된다.</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AssessmentResultReadyEvent event) {
        try {
            competencyDiagnosisMileageAccrualService.accrueAssessmentCompletion(event.attemptId());
        } catch (RuntimeException e) {
            log.error("역량진단 완료 마일리지 적립 실패 — attemptId={}", event.attemptId(), e);
        }
    }
}
