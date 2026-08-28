package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.CompetencySummary;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

// 레포지토리를 목으로 두므로 "활성·최상위·정렬" 필터 자체(그건 Spring Data 파생 쿼리 몫)가 아니라,
// 조회 결과를 순서 그대로 CompetencySummary로 옮기는지, 결과가 없을 때 널이 아닌 빈 리스트를 주는지를 검증한다.
@ExtendWith(MockitoExtension.class)
class CompetencyQueryServiceTest {

    @Mock
    CompetencyRepository competencyRepository;

    @InjectMocks
    CompetencyQueryService competencyQueryService;

    @Test
    void getActiveTopLevelCompetencies_mapsEntitiesPreservingRepositoryOrder() {
        when(competencyRepository.findByParentCompetencyIsNullAndActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(
                        Competency.createTop("C100", "자기관리 역량", null, null, 100, 1),
                        Competency.createTop("C200", "의사소통 역량", null, null, 200, 1)));

        List<CompetencySummary> result = competencyQueryService.getActiveTopLevelCompetencies();

        assertThat(result).extracting(CompetencySummary::competencyCode)
                .containsExactly("C100", "C200");
        assertThat(result).extracting(CompetencySummary::competencyName)
                .containsExactly("자기관리 역량", "의사소통 역량");
        assertThat(result).extracting(CompetencySummary::displayOrder)
                .containsExactly(100, 200);
    }

    @Test
    void getActiveTopLevelCompetencies_whenNone_returnsEmptyList() {
        when(competencyRepository.findByParentCompetencyIsNullAndActiveTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of());

        assertThat(competencyQueryService.getActiveTopLevelCompetencies()).isEmpty();
    }
}
