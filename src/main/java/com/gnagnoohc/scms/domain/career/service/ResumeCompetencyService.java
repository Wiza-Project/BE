package com.gnagnoohc.scms.domain.career.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnagnoohc.scms.domain.career.dto.competency.ResumeCompetencyResponse;
import com.gnagnoohc.scms.domain.career.dto.competency.ResumeCompetencyResponse.CompetencyScoreDto;
import com.gnagnoohc.scms.domain.career.entity.ResumeCompetencySnapshot;
import com.gnagnoohc.scms.domain.career.event.ResumeCompetencySyncRequestedEvent;
import com.gnagnoohc.scms.domain.career.repository.ResumeCompetencySnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 이력서 화면의 핵심역량 연동 결과 조회 및 재연동 요청.
 * 실제 값 적재는 {@link com.gnagnoohc.scms.domain.career.listener.ResumeCompetencySnapshotEventListener}가
 * 담당하고, 이 서비스는 읽기 모델을 조회하거나 재연동 이벤트를 발행하는 역할만 한다.
 */
@Service
@RequiredArgsConstructor
public class ResumeCompetencyService {

    private final ResumeCompetencySnapshotRepository resumeCompetencySnapshotRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public ResumeCompetencyResponse getLatest(Integer studentId) {
        return resumeCompetencySnapshotRepository.findByStudent_UserId(studentId)
                .map(this::toResponse)
                .orElseGet(ResumeCompetencyResponse::notSynced);
    }

    /**
     * 재연동 요청 이벤트를 발행한다.
     *
     * <p>이 메서드는 트랜잭션을 열지 않는다 — {@code ResumeCompetencySyncRequestedEvent}는 비트랜잭션
     * 컨텍스트에서 발행돼야 한다({@link ResumeCompetencySyncRequestedEvent} 문서 참고). 핵심역량 리스너와
     * 이 도메인의 {@code AFTER_COMMIT} 리스너 모두 기본 동기 처리(@Async 없음)라, publishEvent 호출이
     * 끝나는 시점엔 upsert까지 이미 반영돼 있다 — 그래서 바로 이어서 최신 상태를 다시 읽어 응답한다.</p>
     */
    public ResumeCompetencyResponse refresh(Integer studentId) {
        UUID requestId = UUID.randomUUID();
        eventPublisher.publishEvent(new ResumeCompetencySyncRequestedEvent(studentId, requestId, Instant.now()));
        return getLatest(studentId);
    }

    private ResumeCompetencyResponse toResponse(ResumeCompetencySnapshot snapshot) {
        if (!snapshot.isReady()) {
            return new ResumeCompetencyResponse(
                    snapshot.getStatus(), null, null, null, null, null, null, null, null,
                    snapshot.getUnavailableReason(), snapshot.getSyncedAt());
        }
        return new ResumeCompetencyResponse(
                snapshot.getStatus(),
                snapshot.getAttemptId(),
                snapshot.getAssessmentName(),
                snapshot.getAcademicYear(),
                snapshot.getSemesterLabel(),
                snapshot.getAssessmentPhase(),
                snapshot.getSubmittedAt(),
                snapshot.getOverallAverageScore(),
                readScores(snapshot.getScores()),
                null,
                snapshot.getSyncedAt());
    }

    private List<CompetencyScoreDto> readScores(JsonNode scoresNode) {
        if (scoresNode == null || scoresNode.isNull()) {
            return List.of();
        }
        return objectMapper.convertValue(scoresNode, new TypeReference<List<CompetencyScoreDto>>() {});
    }
}
