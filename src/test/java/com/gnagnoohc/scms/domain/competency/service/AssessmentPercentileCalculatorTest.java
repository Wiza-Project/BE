package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentPercentileCalculatorTest {

    private final AssessmentPercentileCalculator calculator = new AssessmentPercentileCalculator();

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
    void calculate_singleScoreInCompetency_isHundredPercentile() {
        Competency competency = buildCompetency(1);
        AssessmentScore score = buildScore(1, competency, 80);

        Map<Integer, BigDecimal> result = calculator.calculate(List.of(score));

        assertThat(result.get(1)).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    // 3명 중 60점(최하위)은 자신 1명만 이하이므로 33.333%, 80점(중위)은 자신 포함 2명 이하이므로 66.667%,
    // 100점(최상위)은 3명 모두 이하이므로 100% — "내 점수 이하 비율" 정의를 그대로 검증한다.
    @Test
    void calculate_ranksByConvertedScoreAscending_highestScoreGetsHighestPercentile() {
        Competency competency = buildCompetency(1);
        AssessmentScore low = buildScore(1, competency, 60);
        AssessmentScore mid = buildScore(2, competency, 80);
        AssessmentScore high = buildScore(3, competency, 100);

        Map<Integer, BigDecimal> result = calculator.calculate(List.of(low, mid, high));

        assertThat(result.get(1)).isEqualByComparingTo(new BigDecimal("33.333"));
        assertThat(result.get(2)).isEqualByComparingTo(new BigDecimal("66.667"));
        assertThat(result.get(3)).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void calculate_tiedScores_getSamePercentile() {
        Competency competency = buildCompetency(1);
        AssessmentScore tied1 = buildScore(1, competency, 80);
        AssessmentScore tied2 = buildScore(2, competency, 80);

        Map<Integer, BigDecimal> result = calculator.calculate(List.of(tied1, tied2));

        assertThat(result.get(1)).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(result.get(2)).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void calculate_multipleCompetencies_calculatesEachGroupIndependently() {
        Competency competency1 = buildCompetency(1);
        Competency competency2 = buildCompetency(2);
        AssessmentScore c1Score = buildScore(1, competency1, 50);
        AssessmentScore c2ScoreLow = buildScore(2, competency2, 20);
        AssessmentScore c2ScoreHigh = buildScore(3, competency2, 90);

        Map<Integer, BigDecimal> result = calculator.calculate(List.of(c1Score, c2ScoreLow, c2ScoreHigh));

        assertThat(result.get(1)).isEqualByComparingTo(BigDecimal.valueOf(100)); // 역량1은 혼자뿐
        assertThat(result.get(2)).isEqualByComparingTo(BigDecimal.valueOf(50));  // 역량2 하위
        assertThat(result.get(3)).isEqualByComparingTo(BigDecimal.valueOf(100)); // 역량2 상위
    }
}
