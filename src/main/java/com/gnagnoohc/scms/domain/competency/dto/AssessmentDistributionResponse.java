package com.gnagnoohc.scms.domain.competency.dto;

import java.math.BigDecimal;
import java.util.List;

public record AssessmentDistributionResponse(
        Integer assessmentRoundId,
        AssessmentGroupAxis groupAxis,
        List<GroupScores> groups
) {
    // FE는 이 하나의 구조를 역량별 분포(competencyAverages를 competency 축으로 다시 묶어 그림)와
    // 집단별 비교(groups를 그대로 막대그래프 축으로 사용) 두 그래프 모두에 재사용한다.
    public record GroupScores(
            String groupKey,
            String groupLabel,
            long respondentCount,
            List<CompetencyAverage> competencyAverages
    ) {}

    // 방사형 차트와 축 순서를 맞추기 위해 displayOrder를 함께 내려준다.
    public record CompetencyAverage(
            Integer competencyId,
            String competencyName,
            Integer displayOrder,
            BigDecimal averageScore
    ) {}
}
