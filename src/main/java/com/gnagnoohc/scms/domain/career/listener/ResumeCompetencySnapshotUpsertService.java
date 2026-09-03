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

/**
 * {@link ResumeCompetencySnapshotEventListener}가 위임하는 실제 upsert 트랜잭션.
 *
 * <p>별도 빈으로 분리한 이유는 재시도 때문이다 — {@code student_id} 유니크 제약 위반으로 이 클래스의
 * {@code REQUIRES_NEW} 트랜잭션이 롤백된 뒤, 호출부(리스너)가 "완전히 새로운 트랜잭션 + 새 영속성
 * 컨텍스트"로 재시도해야 안전하다. 같은 클래스 안에서 {@code this.applyReady(...)}로 호출하면 Spring AOP
 * 프록시를 우회해(자기호출) 트랜잭션이 새로 열리지 않으므로, 호출부와 실행부를 서로 다른 빈으로 나눴다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeCompetencySnapshotUpsertService {

    private final ResumeCompetencySnapshotRepository resumeCompetencySnapshotRepository;
    private final AppUserRepository appUserRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** {@code saveAndFlush}로 유니크 제약 위반을 이 메서드 안에서(트랜잭션 커밋을 기다리지 않고) 바로 드러낸다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyReady(AssessmentResultReadyEvent event) {
        ResumeCompetencySnapshot snapshot = findOrCreate(event.studentId());

        if (snapshot.isStaleReadyEvent(event.attemptId())) {
            // 더 최신(또는 동일) attemptId가 이미 반영돼 있음 — 순서가 뒤바뀐 재전달로 보고 건너뛴다.
            log.info("이력서 핵심역량 스냅샷 갱신 건너뜀(오래된 이벤트) — studentId={}, 수신 attemptId={}, 기존 attemptId={}",
                    event.studentId(), event.attemptId(), snapshot.getAttemptId());
            return;
        }

        JsonNode scoresNode = objectMapper.valueToTree(event.scores());
        snapshot.applyReady(event.attemptId(), event.assessmentRoundId(), event.assessmentName(), event.academicYear(),
                event.semesterCode(), event.semesterLabel(), event.assessmentPhase(), event.submittedAt(),
                event.overallAverageScore(), scoresNode, event.requestId(), event.submittedAt());
        resumeCompetencySnapshotRepository.saveAndFlush(snapshot);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyUnavailable(AssessmentResultUnavailableEvent event) {
        ResumeCompetencySnapshot snapshot = findOrCreate(event.studentId());

        if (snapshot.isReady()) {
            // 완료 진단이 있다는 스냅샷을 "결과 없음"으로 되돌리지 않는다 — 완료된 진단은 취소되지 않으므로
            // 이 상황은 이벤트 순서 역전(오래된 결과없음 응답이 뒤늦게 도착)으로만 생긴다.
            log.info("이력서 핵심역량 스냅샷 갱신 건너뜀(이미 READY) — studentId={}", event.studentId());
            return;
        }

        snapshot.applyUnavailable(event.reason(), event.requestId(), event.occurredAt());
        resumeCompetencySnapshotRepository.saveAndFlush(snapshot);
    }

    /** 동시 최초 생성 경합 시 두 요청 모두 이 분기를 타고 둘 다 새 엔티티를 만들 수 있다 — 그중 하나만 insert에 성공한다. */
    private ResumeCompetencySnapshot findOrCreate(Integer studentId) {
        return resumeCompetencySnapshotRepository.findByStudent_UserId(studentId)
                .orElseGet(() -> ResumeCompetencySnapshot.createFor(appUserRepository.getReferenceById(studentId)));
    }
}
