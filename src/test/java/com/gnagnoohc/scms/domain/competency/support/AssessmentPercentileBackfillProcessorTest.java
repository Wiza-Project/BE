package com.gnagnoohc.scms.domain.competency.support;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentScoreQueryRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentScoreRepository;
import com.gnagnoohc.scms.domain.competency.service.AssessmentPercentileCalculator;
import com.gnagnoohc.scms.domain.competency.support.AssessmentPercentileBackfillProcessor.RoundResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentPercentileBackfillProcessorTest {

    private static final Integer ROUND_ID = 20;

    @Mock
    AssessmentScoreQueryRepository assessmentScoreQueryRepository;

    @Mock
    AssessmentScoreRepository assessmentScoreRepository;

    // AssessmentPercentileCalculator는 리포지토리 의존성 없는 순수 계산 컴포넌트라 목 대신 실제 인스턴스를 쓴다
    // (AssessmentPercentileBatchServiceTest와 같은 패턴).
    AssessmentPercentileBackfillProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AssessmentPercentileBackfillProcessor(
                assessmentScoreQueryRepository, assessmentScoreRepository, new AssessmentPercentileCalculator());
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
    void recalculateRound_recalculatesEnrolledPercentiles_andNullsNonEnrolled_withoutTouchingRoundStatus() {
        Competency competency = buildCompetency(1);
        AssessmentScore low = buildScore(1, competency, 60);
        AssessmentScore high = buildScore(2, competency, 100);
        when(assessmentScoreRepository.nullifyNonEnrolledPercentiles(ROUND_ID)).thenReturn(3);
        when(assessmentScoreQueryRepository.findEnrolledScoresByRoundIdFetchCompetency(ROUND_ID))
                .thenReturn(List.of(low, high));

        RoundResult result = processor.recalculateRound(ROUND_ID);

        // 재학생 행은 재학생 모수 기준으로 재계산된다.
        assertThat(low.getPercentile()).isEqualByComparingTo(new BigDecimal("50.000"));
        assertThat(high.getPercentile()).isEqualByComparingTo(BigDecimal.valueOf(100));
        verify(assessmentScoreRepository).saveAll(List.of(low, high));

        assertThat(result.recalculatedScoreRows()).isEqualTo(2);
        assertThat(result.nulledScoreRows()).isEqualTo(3);
        assertThat(result.hadNoEnrolledScores()).isFalse();
        // AssessmentRound 리포지토리 의존성 자체가 없어 round_status는 구조적으로 변경 불가하다.
    }

    @Test
    void recalculateRound_withNoEnrolledScores_nullsNonEnrolledOnly_andDoesNotSave() {
        when(assessmentScoreRepository.nullifyNonEnrolledPercentiles(ROUND_ID)).thenReturn(5);
        when(assessmentScoreQueryRepository.findEnrolledScoresByRoundIdFetchCompetency(ROUND_ID))
                .thenReturn(List.of());

        RoundResult result = processor.recalculateRound(ROUND_ID);

        verify(assessmentScoreRepository, never()).saveAll(anyList());
        assertThat(result.recalculatedScoreRows()).isZero();
        assertThat(result.nulledScoreRows()).isEqualTo(5);
        assertThat(result.hadNoEnrolledScores()).isTrue();
    }
}
