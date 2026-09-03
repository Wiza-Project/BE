package com.gnagnoohc.scms.domain.competency.support;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResultResponse.CompetencyResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WeakCompetencySelectorTest {

    private final WeakCompetencySelector selector = new WeakCompetencySelector();

    private static CompetencyResult score(int competencyId, int displayOrder, Double converted) {
        return new CompetencyResult(competencyId, "역량" + competencyId, displayOrder,
                converted == null ? null : BigDecimal.valueOf(converted), null);
    }

    @Test
    void select_returnsTwoLowestByConvertedScore() {
        List<CompetencyResult> selected = selector.select(List.of(
                score(1, 1, 72.0), score(2, 2, 40.0), score(3, 3, 55.0),
                score(4, 4, 90.0), score(5, 5, 33.0), score(6, 6, 61.0)));

        assertThat(selected).extracting(CompetencyResult::competencyId).containsExactly(5, 2);
    }

    @Test
    void select_breaksTiesByDisplayOrder() {
        List<CompetencyResult> selected = selector.select(List.of(
                score(3, 3, 50.0), score(1, 1, 50.0), score(2, 2, 80.0)));

        // 동점(50.0)이면 축순서(displayOrder)가 앞선 역량이 먼저.
        assertThat(selected).extracting(CompetencyResult::competencyId).containsExactly(1, 3);
    }

    @Test
    void select_whenFewerThanLimitScored_returnsAllPresent() {
        List<CompetencyResult> selected = selector.select(List.of(score(1, 1, 42.0)));

        assertThat(selected).extracting(CompetencyResult::competencyId).containsExactly(1);
    }

    @Test
    void select_ignoresCompetenciesWithoutConvertedScore() {
        List<CompetencyResult> selected = selector.select(List.of(
                score(1, 1, null), score(2, 2, 88.0), score(3, 3, 47.0)));

        assertThat(selected).extracting(CompetencyResult::competencyId).containsExactly(3, 2);
    }

    @Test
    void select_whenNoneScored_returnsEmpty() {
        assertThat(selector.select(List.of(score(1, 1, null), score(2, 2, null)))).isEmpty();
    }
}
