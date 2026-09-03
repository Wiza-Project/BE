package com.gnagnoohc.scms.domain.career.listener;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnagnoohc.scms.domain.career.entity.ResumeCompetencySnapshot;
import com.gnagnoohc.scms.domain.career.repository.ResumeCompetencySnapshotRepository;
import com.gnagnoohc.scms.domain.competency.event.AssessmentResultReadyEvent;
import com.gnagnoohc.scms.domain.competency.event.AssessmentResultUnavailableEvent;
import com.gnagnoohc.scms.domain.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 핵심역량 도메인의 이력서 연동 이벤트를 받아 {@link ResumeCompetencySnapshot}(읽기 모델)에 upsert한다.
 *
 * <p>두 이벤트 모두 핵심역량 쪽 자체 트랜잭션 안에서 발행된다({@code AssessmentResultReadyEvent},
 * {@code AssessmentResultUnavailableEvent} 문서 참고) — 그래서 {@code AFTER_COMMIT}으로 구독한다.
 * 그 트랜잭션이 롤백되면 이 쪽도 반영하지 않아야 하고, 커밋된 뒤에만 upsert가 유효하기 때문이다.
 * 리스너 메서드 자체엔 {@code REQUIRES_NEW}로 별도 트랜잭션을 연다 — 원본 트랜잭션은 이미 커밋 완료 상태라
 * 참여할 트랜잭션이 없고, Spring도 AFTER_COMMIT 리스너에 기본(REQUIRED) {@code @Transactional}을
 * 붙이는 걸 기동 시점에 막는다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeCompetencySnapshotEventListener {

    private final ResumeCompetencySnapshotRepository resumeCompetencySnapshotRepository;
    private final AppUserRepository appUserRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleReady(AssessmentResultReadyEvent event) {
        ResumeCompetencySnapshot snapshot = findOrCreate(event.studentId());

        if (snapshot.isStaleReadyEvent(event.attemptId())) {
            // 더 최신(또는 동일) attemptId가 이미 반영돼 있음
            log.info("이력서 핵심역량 스냅샷 갱신 건너뜀(오래된 이벤트) — studentId={}, 수신 attemptId={}, 기존 attemptId={}",
                    event.studentId(), event.attemptId(), snapshot.getAttemptId());
            return;
        }

        JsonNode scoresNode = objectMapper.valueToTree(event.scores());
        snapshot.applyReady(event.attemptId(), event.assessmentRoundId(), event.assessmentName(), event.academicYear(),
                event.semesterCode(), event.semesterLabel(), event.assessmentPhase(), event.submittedAt(),
                event.overallAverageScore(), scoresNode, event.requestId(), event.submittedAt());
        resumeCompetencySnapshotRepository.save(snapshot);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleUnavailable(AssessmentResultUnavailableEvent event) {
        ResumeCompetencySnapshot snapshot = findOrCreate(event.studentId());

        if (snapshot.isReady()) {
            // 완료 진단이 있다는 스냅샷을 "결과 없음"으로 되돌리지 않는다 — 완료된 진단은 취소되지 않으므로
            // 이 상황은 이벤트 순서 역전(오래된 결과없음 응답이 뒤늦게 도착)으로만 생긴다.
            log.info("이력서 핵심역량 스냅샷 갱신 건너뜀(이미 READY) — studentId={}", event.studentId());
            return;
        }

        snapshot.applyUnavailable(event.reason(), event.requestId(), event.occurredAt());
        resumeCompetencySnapshotRepository.save(snapshot);
    }

    private ResumeCompetencySnapshot findOrCreate(Integer studentId) {
        return resumeCompetencySnapshotRepository.findByStudent_UserId(studentId)
                .orElseGet(() -> ResumeCompetencySnapshot.createFor(appUserRepository.getReferenceById(studentId)));
    }
}
