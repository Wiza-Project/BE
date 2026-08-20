package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.CompetencyRegisterRequest;
import com.gnagnoohc.scms.domain.competency.dto.CompetencyResponse;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.CompetencyRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompetencyServiceTest {

    @Mock
    CompetencyRepository competencyRepository;

    @InjectMocks
    CompetencyService competencyService;

    @Test
    void registerCompetency_assignsSequentialCodeAndOrder() {
        when(competencyRepository.countByParentCompetencyIsNull()).thenReturn(3L);
        when(competencyRepository.save(any(Competency.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CompetencyResponse response = competencyService.registerCompetency(
                new CompetencyRegisterRequest("문제해결역량", "Problem Solving", "설명"), 1);

        assertThat(response.competencyCode()).isEqualTo("C4");
        assertThat(response.displayOrder()).isEqualTo(4);
        assertThat(response.competencyName()).isEqualTo("문제해결역량");
        assertThat(response.active()).isTrue();
    }

    @Test
    void registerCompetency_whenAlreadySix_throwsLimitExceeded() {
        when(competencyRepository.countByParentCompetencyIsNull()).thenReturn(6L);

        assertThatThrownBy(() -> competencyService.registerCompetency(
                new CompetencyRegisterRequest("초과역량", null, null), 1))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMPETENCY_LIMIT_EXCEEDED);
    }
}
