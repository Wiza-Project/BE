package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentDistributionResponse;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentDistributionResponse.CompetencyAverage;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentDistributionResponse.GroupScores;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentGroupAxis;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentDistributionQueryRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentDistributionQueryRepository.GroupCompetencyAggregate;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssessmentDistributionService {

    private final AssessmentRoundRepository assessmentRoundRepository;
    private final AssessmentDistributionQueryRepository assessmentDistributionQueryRepository;

    public AssessmentDistributionResponse getDistribution(Integer roundId, String groupByParam) {
        if (!assessmentRoundRepository.existsById(roundId)) {
            throw new BusinessException(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND);
        }
        AssessmentGroupAxis groupAxis = parseGroupAxis(groupByParam);

        List<GroupCompetencyAggregate> rows =
                assessmentDistributionQueryRepository.aggregateByGroupAxis(roundId, groupAxis);

        // 쿼리가 이미 groupKey 순으로 내려주므로 LinkedHashMap으로 그 순서를 그대로 보존한다.
        Map<String, List<GroupCompetencyAggregate>> byGroup = rows.stream()
                .collect(Collectors.groupingBy(GroupCompetencyAggregate::groupKey, LinkedHashMap::new, Collectors.toList()));

        List<GroupScores> groups = byGroup.values().stream()
                .map(this::toGroupScores)
                .toList();

        return new AssessmentDistributionResponse(roundId, groupAxis, groups);
    }

    /**
     * GRADE/MAJOR 둘 중 하나가 아니면 Q021로 차단한다 — TargetConditionInterpreter가 인식 못 하는
     * target_condition 키를 조용히 무시하지 않고 에러로 실패시키는 것과 같은 이유(잘못된 값을 무시하면
     * FE가 의도한 축과 다른 데이터를 받고도 알아채기 어렵다).
     */
    private AssessmentGroupAxis parseGroupAxis(String groupByParam) {
        try {
            return AssessmentGroupAxis.valueOf(groupByParam.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.ASSESSMENT_INVALID_GROUP_AXIS);
        }
    }

    private GroupScores toGroupScores(List<GroupCompetencyAggregate> groupRows) {
        GroupCompetencyAggregate first = groupRows.get(0);
        List<CompetencyAverage> competencyAverages = groupRows.stream()
                // 방사형 차트와 축 순서를 맞추기 위해 displayOrder로 다시 정렬한다.
                .sorted(Comparator.comparing(GroupCompetencyAggregate::displayOrder))
                .map(r -> new CompetencyAverage(r.competencyId(), r.competencyName(), r.displayOrder(), r.averageScore()))
                .toList();
        return new GroupScores(first.groupKey(), first.groupLabel(), first.respondentCount(), competencyAverages);
    }
}
