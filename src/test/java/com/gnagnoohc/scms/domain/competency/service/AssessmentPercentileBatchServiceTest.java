package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentPercentileBatchServiceTest {

    private static final Integer ROUND_ID = 20;

    @Mock
    AssessmentRoundRepository assessmentRoundRepository;

    @Mock
    AssessmentScoreRepository assessmentScoreRepository;

    // AssessmentPercentileCalculator는 리포지토리 의존성 없는 순수 계산 컴포넌트라 목 대신 실제 인스턴스를 사용한다
    // (AssessmentSubmissionServiceTest와 같은 패턴).
    AssessmentPercentileBatchService assessmentPercentileBatchService;

    @BeforeEach
    void setUp() {
        assessmentPercentileBatchService = new AssessmentPercentileBatchService(
                assessmentRoundRepository, assessmentScoreRepository, new AssessmentPercentileCalculator());
    }

    private static AssessmentRound buildRound(Instant endsAt) {
        AssessmentRound round = AssessmentRound.create(
                "2026학년도 1학기 사전진단", 2026, "SPRING", "PRE",
                endsAt.minus(7, ChronoUnit.DAYS), endsAt, null, 1);
        ReflectionTestUtils.setField(round, "assessmentRoundId", ROUND_ID);
        return round;
    }

    private static Competency buildCompetency(Integer competencyId) {
        Competency competency = Competency.createTop("C1", "문제해결역량", null, null, 1, 1);
        ReflectionTestUtils.setField(competency, "competencyId", competencyId);
        return competency;
    }

    private static AssessmentScore buildScore(Integer scoreId, Competency competency, int convertedScore) {
        AssessmentScore score = AssessmentScore.create(
                (AssessmentAttempt) null, competency, BigDecimal.valueOf(1), BigDecimal.valueOf(convertedScore));
        ReflectionTestUtils.setField(score, "assessmentScoreId", scoreId);
        return score;
    }

    @Test
    void calculatePercentilesForEndedRounds_fillsPercentileAndMarksRoundCompleted() {
        AssessmentRound round = buildRound(Instant.now().minus(1, ChronoUnit.DAYS));
        Competency competency = buildCompetency(1);
        AssessmentScore low = buildScore(1, competency, 60);
        AssessmentScore high = buildScore(2, competency, 100);

        when(assessmentRoundRepository.findByEndsAtBeforeAndRoundStatusNot(any(), anyString()))
                .thenReturn(List.of(round));
        when(assessmentScoreRepository.findByRoundIdFetchCompetency(ROUND_ID))
                .thenReturn(List.of(low, high));

        int processedCount = assessmentPercentileBatchService.calculatePercentilesForEndedRounds();

        assertThat(processedCount).isEqualTo(1);
        assertThat(low.getPercentile()).isEqualByComparingTo(new BigDecimal("50.000"));
        assertThat(high.getPercentile()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(round.getRoundStatus()).isEqualTo("COMPLETED");
    }

    // 응시자가 0명인 회차(제출자가 아무도 없는 채로 기간만 끝난 경우)도 완료 처리해야 다음 배치 사이클에서
    // 계속 대상으로 잡혀 헛도는 걸 막을 수 있다.
    @Test
    void calculatePercentilesForEndedRounds_roundWithNoScores_stillMarksCompleted() {
        AssessmentRound round = buildRound(Instant.now().minus(1, ChronoUnit.DAYS));

        when(assessmentRoundRepository.findByEndsAtBeforeAndRoundStatusNot(any(), anyString()))
                .thenReturn(List.of(round));
        when(assessmentScoreRepository.findByRoundIdFetchCompetency(ROUND_ID))
                .thenReturn(List.of());

        int processedCount = assessmentPercentileBatchService.calculatePercentilesForEndedRounds();

        assertThat(processedCount).isEqualTo(1);
        assertThat(round.getRoundStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void calculatePercentilesForEndedRounds_noTargetRounds_doesNothing() {
        when(assessmentRoundRepository.findByEndsAtBeforeAndRoundStatusNot(any(), anyString()))
                .thenReturn(List.of());

        int processedCount = assessmentPercentileBatchService.calculatePercentilesForEndedRounds();

        assertThat(processedCount).isEqualTo(0);
        verify(assessmentScoreRepository, never()).findByRoundIdFetchCompetency(any());
    }

    // endsAt 직전에 시작된 제출이 endsAt을 넘겨서야 커밋되는 경합(회차가 먼저 COMPLETED로 확정되면
    // 그 학생의 percentile이 영영 NULL로 남는 문제)을 막기 위해 완료 판정 시각을 그대로 now()로 넘기지
    // 않고 유예시간만큼 과거로 당겨서 넘기는지 검증한다.
    @Test
    void calculatePercentilesForEndedRounds_queriesWithGracePeriodBeforeNow() {
        when(assessmentRoundRepository.findByEndsAtBeforeAndRoundStatusNot(any(), anyString()))
                .thenReturn(List.of());

        Instant beforeCall = Instant.now();
        assessmentPercentileBatchService.calculatePercentilesForEndedRounds();
        Instant afterCall = Instant.now();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(assessmentRoundRepository).findByEndsAtBeforeAndRoundStatusNot(cutoffCaptor.capture(), anyString());

        Instant cutoff = cutoffCaptor.getValue();
        assertThat(cutoff).isAfterOrEqualTo(beforeCall.minus(Duration.ofMinutes(5)));
        assertThat(cutoff).isBeforeOrEqualTo(afterCall.minus(Duration.ofMinutes(5)));
    }
}
