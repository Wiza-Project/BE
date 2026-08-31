package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentResultResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import com.gnagnoohc.scms.domain.competency.event.AssessmentResultReadyEvent;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentScoreRepository;
import com.gnagnoohc.scms.global.common.service.CommonCodeService;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * {@link AssessmentResultReadyEvent} 발행을 한곳에 모은 공용 컴포넌트.
 * 제출 완료 경로, 취창업 재연동 요청 처리, 초기 백필이 모두 이 클래스를 통해 발행한다.
 *
 * <p>두 진입점의 차이는 "점수를 이미 손에 들고 있는가"이다.
 * <ul>
 *   <li>{@link #publishFromSubmit} — 제출 트랜잭션이 방금 계산한 점수 리스트를 그대로 재사용한다(재조회 없음).
 *       호출자(AssessmentSubmissionService)의 트랜잭션 안에서 실행된다.</li>
 *   <li>{@link #publish} — attemptId만으로 attempt·점수를 로드해 조립한다. 지연 로딩이 필요해
 *       자체 트랜잭션을 연다(재연동 리스너는 이미 트랜잭션 안이라 그 트랜잭션에 참여).</li>
 * </ul>
 *
 * <p>환산점수가 없으면(문항 0개 회차 제출 등) 전체 평균을 낼 수 없어 이벤트를 발행하지 않고
 * {@code false}를 반환한다. 호출자는 이 값으로 "발행함/건너뜀"을 구분한다 — 재연동 리스너는
 * 건너뜀일 때 결과 없음 이벤트로 대체하고, 백필은 건너뜀을 발행 건수와 분리해 집계한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssessmentResultReadyEventPublisher {

    private static final String SEMESTER_CODE_GROUP = "SEMESTER";

    private final ApplicationEventPublisher eventPublisher;
    private final AssessmentAttemptRepository assessmentAttemptRepository;
    private final AssessmentScoreRepository assessmentScoreRepository;
    private final CommonCodeService commonCodeService;

    /**
     * 제출 완료 직후 호출 — 점수 재조회 없이 계산 결과를 그대로 쓰고 requestId는 null.
     *
     * @return 이벤트를 발행했으면 {@code true}, 환산점수가 없어 건너뛰었으면 {@code false}
     */
    public boolean publishFromSubmit(AssessmentAttempt attempt,
                                     List<AssessmentScoreCalculator.CompetencyScore> calculatedScores) {
        if (calculatedScores.isEmpty()) {
            // 회차에 문항이 하나도 매핑되지 않아 환산점수가 비면 전체 평균을 낼 수 없다(0으로 나누기).
            // publish(attemptId, ...) 경로의 빈 점수 가드와 동일하게, 이벤트를 발행하지 않고 건너뛴다.
            log.warn("이력서 연동 결과 준비 이벤트 스킵 — attemptId={} 환산점수 없음", attempt.getAttemptId());
            return false;
        }

        List<AssessmentResultReadyEvent.CompetencyScore> scores = calculatedScores.stream()
                .map(s -> new AssessmentResultReadyEvent.CompetencyScore(
                        s.competency().getCompetencyId(),
                        s.competency().getCompetencyName(),
                        s.competency().getDisplayOrder(),
                        s.convertedScore()))
                .sorted(Comparator.comparing(AssessmentResultReadyEvent.CompetencyScore::displayOrder))
                .toList();

        BigDecimal overallAverageScore = AssessmentResultResponse.averageConvertedScore(
                calculatedScores.stream()
                        .map(AssessmentScoreCalculator.CompetencyScore::convertedScore)
                        .toList());

        eventPublisher.publishEvent(assemble(attempt, scores, overallAverageScore, null));
        return true;
    }

    /**
     * attemptId로 attempt·점수를 로드해 발행한다. 백필은 {@code requestId = null}로,
     * 재연동 처리는 요청의 requestId를 그대로 전달한다.
     *
     * @return 이벤트를 발행했으면 {@code true}, 환산점수가 없어 건너뛰었으면 {@code false}
     */
    @Transactional
    public boolean publish(Integer attemptId, UUID requestId) {
        AssessmentAttempt attempt = assessmentAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_ATTEMPT_NOT_FOUND));

        // 이미 displayOrder 정렬 + competency fetch join 되어 있다.
        List<AssessmentScore> scoreEntities =
                assessmentScoreRepository.findByAttemptIdFetchCompetencyOrderByDisplayOrder(attemptId);
        if (scoreEntities.isEmpty()) {
            // submittedAt이 채워졌는데 환산점수가 없는 건 데이터 이상 — 재발행/백필 모두 조용히 건너뛴다.
            log.warn("이력서 연동 결과 준비 이벤트 스킵 — attemptId={} 환산점수 없음", attemptId);
            return false;
        }

        List<AssessmentResultReadyEvent.CompetencyScore> scores = scoreEntities.stream()
                .map(s -> new AssessmentResultReadyEvent.CompetencyScore(
                        s.getCompetency().getCompetencyId(),
                        s.getCompetency().getCompetencyName(),
                        s.getCompetency().getDisplayOrder(),
                        s.getConvertedScore()))
                .toList();

        BigDecimal overallAverageScore = AssessmentResultResponse.averageConvertedScore(
                scoreEntities.stream().map(AssessmentScore::getConvertedScore).toList());

        eventPublisher.publishEvent(assemble(attempt, scores, overallAverageScore, requestId));
        return true;
    }

    private AssessmentResultReadyEvent assemble(AssessmentAttempt attempt,
                                               List<AssessmentResultReadyEvent.CompetencyScore> scores,
                                               BigDecimal overallAverageScore,
                                               UUID requestId) {
        AssessmentRound round = attempt.getAssessmentRound();
        return new AssessmentResultReadyEvent(
                attempt.getStudent().getUserId(),
                attempt.getAttemptId(),
                round.getAssessmentRoundId(),
                round.getAssessmentName(),
                round.getAcademicYear(),
                round.getSemesterCode(),
                resolveSemesterLabel(round.getSemesterCode()),
                round.getAssessmentType(),
                attempt.getSubmittedAt(),
                overallAverageScore,
                scores,
                requestId);
    }

    // SEMESTER 공통코드 미조회 시 CommonCodeService가 코드 원값을 그대로 돌려주므로 별도 fallback 분기가 필요 없다.
    private String resolveSemesterLabel(String semesterCode) {
        return commonCodeService.getCodeName(SEMESTER_CODE_GROUP, semesterCode);
    }
}
