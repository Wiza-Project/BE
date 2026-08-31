package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.CompetencySummary;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

// 레포지토리를 목으로 두므로 "활성·최상위·정렬" 필터 자체(그건 Spring Data 파생 쿼리 몫)가 아니라,
// 조회 결과를 순서 그대로 CompetencySummary로 옮기는지, includeCompetencyId 예외 처리가 규칙대로인지를 검증한다.
@ExtendWith(MockitoExtension.class)
class CompetencyQueryServiceTest {

    @Mock
    CompetencyRepository competencyRepository;

    @InjectMocks
    CompetencyQueryService competencyQueryService;

    // Competency는 세터가 없고 id가 IDENTITY라, 테스트에서 id·활성여부를 채우려면 리플렉션이 필요하다.
    private static Competency competency(int id, String code, String name, int displayOrder, boolean active) {
        Competency competency = Competency.createTop(code, name, null, null, displayOrder, 1);
        ReflectionTestUtils.setField(competency, "competencyId", id);
        if (!active) {
            competency.deactivate();
        }
        return competency;
    }

    private void givenActiveCompetencies(Competency... competencies) {
        when(competencyRepository.findByParentCompetencyIsNullAndActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(competencies));
    }

    @Test
    void getActiveTopLevelCompetencies_mapsAllFieldsPreservingRepositoryOrder() {
        givenActiveCompetencies(
                competency(1, "C100", "자기관리 역량", 100, true),
                competency(2, "C200", "의사소통 역량", 200, true));

        List<CompetencySummary> result = competencyQueryService.getActiveTopLevelCompetencies();

        assertThat(result).extracting(
                        CompetencySummary::competencyId,
                        CompetencySummary::competencyCode,
                        CompetencySummary::competencyName,
                        CompetencySummary::displayOrder,
                        CompetencySummary::active)
                .containsExactly(
                        tuple(1, "C100", "자기관리 역량", 100, true),
                        tuple(2, "C200", "의사소통 역량", 200, true));
    }

    @Test
    void getActiveTopLevelCompetencies_whenNone_returnsEmptyList() {
        when(competencyRepository.findByParentCompetencyIsNullAndActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of());

        assertThat(competencyQueryService.getActiveTopLevelCompetencies()).isEmpty();
    }

    @Test
    void getActiveTopLevelCompetencies_withNullIncludeId_returnsActiveListOnly() {
        givenActiveCompetencies(competency(1, "C100", "자기관리 역량", 100, true));

        assertThat(competencyQueryService.getActiveTopLevelCompetencies(null))
                .extracting(CompetencySummary::competencyId)
                .containsExactly(1);
    }

    @Test
    void getActiveTopLevelCompetencies_withIncludeId_whenAlreadyActive_doesNotDuplicate() {
        givenActiveCompetencies(
                competency(1, "C100", "자기관리 역량", 100, true),
                competency(2, "C200", "의사소통 역량", 200, true));

        List<CompetencySummary> result = competencyQueryService.getActiveTopLevelCompetencies(2);

        assertThat(result).extracting(CompetencySummary::competencyId).containsExactly(1, 2);
    }

    @Test
    void getActiveTopLevelCompetencies_withIncludeId_whenInactive_appendsAtEndWithActiveFalse() {
        givenActiveCompetencies(
                competency(1, "C100", "자기관리 역량", 100, true),
                competency(2, "C200", "의사소통 역량", 200, true));
        when(competencyRepository.findById(9))
                .thenReturn(Optional.of(competency(9, "C900", "폐지된 역량", 900, false)));

        List<CompetencySummary> result = competencyQueryService.getActiveTopLevelCompetencies(9);

        assertThat(result).extracting(CompetencySummary::competencyId).containsExactly(1, 2, 9);
        assertThat(result).last()
                .extracting(CompetencySummary::competencyName, CompetencySummary::active)
                .containsExactly("폐지된 역량", false);
    }

    @Test
    void getActiveTopLevelCompetencies_withIncludeId_whenIdUnknown_ignoresItAndReturnsActiveList() {
        givenActiveCompetencies(competency(1, "C100", "자기관리 역량", 100, true));
        when(competencyRepository.findById(404)).thenReturn(Optional.empty());

        assertThat(competencyQueryService.getActiveTopLevelCompetencies(404))
                .extracting(CompetencySummary::competencyId)
                .containsExactly(1);
    }
}
