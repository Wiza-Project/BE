package com.gnagnoohc.scms.domain.competency.support;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.domain.competency.support.AssessmentPercentileBackfillProcessor.RoundResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentPercentileBackfillRunnerTest {

    @Mock
    AssessmentRoundRepository assessmentRoundRepository;

    @Mock
    AssessmentPercentileBackfillProcessor assessmentPercentileBackfillProcessor;

    @InjectMocks
    AssessmentPercentileBackfillRunner runner;

    private static AssessmentRound buildRound(Integer roundId) {
        AssessmentRound round = AssessmentRound.create(
                "2026학년도 1학기 사전진단", 2026, "SPRING", "PRE",
                Instant.now().minus(7, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS), null, 1);
        ReflectionTestUtils.setField(round, "assessmentRoundId", roundId);
        return round;
    }

    @Test
    void run_recalculatesEveryCompletedRound() {
        when(assessmentRoundRepository.findByRoundStatus("COMPLETED"))
                .thenReturn(List.of(buildRound(1), buildRound(2)));
        when(assessmentPercentileBackfillProcessor.recalculateRound(1)).thenReturn(new RoundResult(3, 2, false));
        when(assessmentPercentileBackfillProcessor.recalculateRound(2)).thenReturn(new RoundResult(0, 1, true));

        runner.run();

        verify(assessmentPercentileBackfillProcessor).recalculateRound(1);
        verify(assessmentPercentileBackfillProcessor).recalculateRound(2);
    }

    @Test
    void run_skipsFailingRound_andContinuesWithTheRest() {
        when(assessmentRoundRepository.findByRoundStatus("COMPLETED"))
                .thenReturn(List.of(buildRound(1), buildRound(2), buildRound(3)));
        when(assessmentPercentileBackfillProcessor.recalculateRound(1)).thenReturn(new RoundResult(1, 0, false));
        doThrow(new RuntimeException("boom")).when(assessmentPercentileBackfillProcessor).recalculateRound(2);
        when(assessmentPercentileBackfillProcessor.recalculateRound(3)).thenReturn(new RoundResult(1, 0, false));

        runner.run(); // 한 회차가 던져도 예외 없이 끝까지 진행

        verify(assessmentPercentileBackfillProcessor).recalculateRound(1);
        verify(assessmentPercentileBackfillProcessor).recalculateRound(3);
    }
}
