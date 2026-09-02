package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentScoreRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 제출 시 같은 트랜잭션에서 산출되는 raw_score/converted_score(AssessmentScoreCalculator)와 달리
 * percentile은 전체 응시자 점수가 갖춰져야 계산 가능하다. 그래서 응시기간(ends_at)이 지난 회차만 골라
 * 회차 종료 후 이 배치가 별도로 채운다 (AssessmentPercentileBatchScheduler가 주기 호출).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AssessmentPercentileBatchService {

    private static final String ROUND_STATUS_COMPLETED = "COMPLETED";

    /**
     * AssessmentAttemptAccessGuard.assertPeriodOpen은 제출 요청이 "시작된 시점"의 now()만 검사한다
     * (check-then-act). endsAt 1초 전에 시작된 제출은 검사를 통과한 뒤 채점·INSERT가 endsAt을 넘겨서야
     * 커밋될 수 있는데, 이 배치가 endsAt 직후 곧바로 도는 사이클에 그 회차를 먼저 COMPLETED로 확정해버리면
     * 뒤늦게 커밋된 그 학생의 assessment_score는 이후 사이클의 조회 대상(round_status<>COMPLETED)에서
     * 영영 빠져 percentile이 NULL로 남는다. 정상적인 제출 트랜잭션(계산+INSERT)은 수백 ms 안에 끝나므로,
     * endsAt 이후 이 유예시간만큼 기다렸다가 완료 처리하면 그 경합 창을 사실상 없앨 수 있다.
     */
    private static final Duration COMPLETION_GRACE_PERIOD = Duration.ofMinutes(5);

    private final AssessmentRoundRepository assessmentRoundRepository;
    private final AssessmentScoreRepository assessmentScoreRepository;
    private final AssessmentPercentileCalculator assessmentPercentileCalculator;

    // 응시자가 0명인 회차도 완료 처리한다 — 그러지 않으면 매 사이클 대상으로 계속 잡혀 배치가 헛돈다.
    public int calculatePercentilesForEndedRounds() {
        Instant completionCutoff = Instant.now().minus(COMPLETION_GRACE_PERIOD);
        List<AssessmentRound> targetRounds =
                assessmentRoundRepository.findByEndsAtBeforeAndRoundStatusNot(completionCutoff, ROUND_STATUS_COMPLETED);

        int processedCount = 0;
        for (AssessmentRound candidate : targetRounds) {
            // AssessmentSubmissionService.submit()도 같은 회차 행에 PESSIMISTIC_WRITE 잠금을 걸고 완료
            // 여부를 재검증하므로, 목록 조회 결과(candidate)를 그대로 쓰지 않고 잠금을 걸어 다시 조회한다.
            AssessmentRound round = assessmentRoundRepository.findByIdForUpdate(candidate.getAssessmentRoundId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND));

            if (round.isPercentileCalculationCompleted()) {
                continue; // 잠금 대기 중 이미 다른 실행에서 완료 처리했다면 다시 계산하지 않는다.
            }

            List<AssessmentScore> scores =
                    assessmentScoreRepository.findByRoundIdFetchCompetency(round.getAssessmentRoundId());

            if (!scores.isEmpty()) {
                Map<Integer, BigDecimal> percentileByScoreId = assessmentPercentileCalculator.calculate(scores);
                for (AssessmentScore score : scores) {
                    score.applyPercentile(percentileByScoreId.get(score.getAssessmentScoreId()));
                }
            }

            round.completePercentileCalculation();
            processedCount++;
        }

        return processedCount;
    }
}
