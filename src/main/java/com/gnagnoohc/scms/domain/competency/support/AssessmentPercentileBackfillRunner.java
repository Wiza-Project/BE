package com.gnagnoohc.scms.domain.competency.support;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.domain.competency.support.AssessmentPercentileBackfillProcessor.RoundResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 백분위 산출 모수를 재학생으로 한정하기 이전에 이미 COMPLETED로 확정된 회차의
 * {@code assessment_score.percentile}을 재학생 한정 모수 기준으로 다시 매기는 1회성 백필 러너.
 *
 * <p>정기 배치(AssessmentPercentileBatchService)는 {@code round_status <> COMPLETED}인 회차만 고르므로
 * 이미 완료된 회차는 영영 재처리되지 않는다 — 그 회차들의 개인 백분위는 비재학생(졸업·휴학 등)이 섞인
 * 분모로 계산된 값이 남아 학생 결과 화면에 그대로 노출된다. 이 러너가 완료 회차를 훑어 바로잡는다.</p>
 *
 * <p>{@code round_status} 변경·이벤트 발행·{@code completePercentileCalculation()} 호출은 없다.
 * 회차별 처리는 {@link AssessmentPercentileBackfillProcessor}가 {@code REQUIRES_NEW}로 격리하므로
 * 한 회차 실패가 나머지를 막지 않는다. 재계산이라 여러 번 돌려도 같은 값이 나와 멱등하다.</p>
 *
 * <p>매 기동마다 전량 재처리하지 않도록 전용 프로필({@code assessment-percentile-backfill})로 가드한다 —
 * 운영자가 이 프로필을 켜고 한 번 기동할 때만 동작하고, 완료 후 프로필을 제거한다.</p>
 */
@Slf4j
@Component
@Profile("assessment-percentile-backfill")
@RequiredArgsConstructor
public class AssessmentPercentileBackfillRunner implements CommandLineRunner {

    private static final String ROUND_STATUS_COMPLETED = "COMPLETED";

    private final AssessmentRoundRepository assessmentRoundRepository;
    private final AssessmentPercentileBackfillProcessor assessmentPercentileBackfillProcessor;

    @Override
    public void run(String... args) {
        List<AssessmentRound> completedRounds = assessmentRoundRepository.findByRoundStatus(ROUND_STATUS_COMPLETED);

        int processedRounds = 0;
        int failedRounds = 0;
        int roundsWithNoEnrolledScores = 0;
        long recalculatedScoreRows = 0;
        long nulledScoreRows = 0;

        for (AssessmentRound round : completedRounds) {
            Integer roundId = round.getAssessmentRoundId();
            try {
                // recalculateRound는 회차당 REQUIRES_NEW 트랜잭션이라, 한 회차 실패가 다른 회차 재계산을
                // 롤백하지 않도록 회차별로 예외를 삼킨다.
                RoundResult result = assessmentPercentileBackfillProcessor.recalculateRound(roundId);
                processedRounds++;
                recalculatedScoreRows += result.recalculatedScoreRows();
                nulledScoreRows += result.nulledScoreRows();
                if (result.hadNoEnrolledScores()) {
                    roundsWithNoEnrolledScores++;
                }
            } catch (Exception e) {
                failedRounds++;
                log.warn("백분위 재계산 백필 실패 — assessmentRoundId={}, 건너뜀", roundId, e);
            }
        }

        log.info("재학생 한정 백분위 재계산 백필 완료 — 처리 회차 {}건, 재계산 점수 {}행, 비재학생 NULL 처리 {}행, "
                        + "재학생 점수 0인 회차 {}건, 실패 회차 {}건",
                processedRounds, recalculatedScoreRows, nulledScoreRows, roundsWithNoEnrolledScores, failedRounds);
    }
}
