package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentDistributionResponse;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentGroupAxis;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentDistributionQueryRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentDistributionQueryRepository.GroupCompetencyAggregate;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentDistributionServiceTest {

    @Mock
    AssessmentRoundRepository assessmentRoundRepository;

    @Mock
    AssessmentDistributionQueryRepository assessmentDistributionQueryRepository;

    @InjectMocks
    AssessmentDistributionService assessmentDistributionService;

    @Test
    void getDistribution_whenRoundNotFound_throwsAssessmentRoundNotFound() {
        when(assessmentRoundRepository.existsById(999)).thenReturn(false);

        assertThatThrownBy(() -> assessmentDistributionService.getDistribution(999, "GRADE"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND);
    }

    @Test
    void getDistribution_whenGroupByUnrecognized_throwsInvalidGroupAxis() {
        when(assessmentRoundRepository.existsById(1)).thenReturn(true);

        assertThatThrownBy(() -> assessmentDistributionService.getDistribution(1, "COLLEGE"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_INVALID_GROUP_AXIS);
    }

    @Test
    void getDistribution_isCaseInsensitiveForGroupByParam() {
        when(assessmentRoundRepository.existsById(1)).thenReturn(true);
        when(assessmentDistributionQueryRepository.aggregateByGroupAxis(1, AssessmentGroupAxis.GRADE))
                .thenReturn(List.of());

        AssessmentDistributionResponse response = assessmentDistributionService.getDistribution(1, "grade");

        assertThat(response.groupAxis()).isEqualTo(AssessmentGroupAxis.GRADE);
    }

    @Test
    void getDistribution_groupsRowsAndOrdersCompetenciesByDisplayOrder() {
        when(assessmentRoundRepository.existsById(1)).thenReturn(true);
        // 쿼리가 이미 displayOrder ASC로 내려주지만, 정렬이 안 됐다고 가정한 순서로 목을 만들어
        // 서비스가 groupingBy 이후 다시 정렬함을 검증한다.
        List<GroupCompetencyAggregate> rows = List.of(
                new GroupCompetencyAggregate("3", "3학년", 2, "문제해결", 2, 70.0, 10L),
                new GroupCompetencyAggregate("3", "3학년", 1, "의사소통", 1, 80.0, 10L),
                new GroupCompetencyAggregate("1", "1학년", 1, "의사소통", 1, 60.0, 5L)
        );
        when(assessmentDistributionQueryRepository.aggregateByGroupAxis(1, AssessmentGroupAxis.GRADE))
                .thenReturn(rows);

        AssessmentDistributionResponse response = assessmentDistributionService.getDistribution(1, "GRADE");

        assertThat(response.assessmentRoundId()).isEqualTo(1);
        assertThat(response.groupAxis()).isEqualTo(AssessmentGroupAxis.GRADE);
        assertThat(response.groups()).hasSize(2);

        AssessmentDistributionResponse.GroupScores grade3 = response.groups().stream()
                .filter(g -> g.groupKey().equals("3"))
                .findFirst().orElseThrow();
        assertThat(grade3.groupLabel()).isEqualTo("3학년");
        assertThat(grade3.respondentCount()).isEqualTo(10L);
        assertThat(grade3.competencyAverages()).extracting(AssessmentDistributionResponse.CompetencyAverage::displayOrder)
                .containsExactly(1, 2);

        AssessmentDistributionResponse.GroupScores grade1 = response.groups().stream()
                .filter(g -> g.groupKey().equals("1"))
                .findFirst().orElseThrow();
        assertThat(grade1.competencyAverages()).hasSize(1);
        assertThat(grade1.competencyAverages().get(0).averageScore()).isEqualByComparingTo(new BigDecimal("60.00"));
    }
}
