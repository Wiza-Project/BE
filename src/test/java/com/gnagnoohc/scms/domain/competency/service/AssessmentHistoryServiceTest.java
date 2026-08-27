package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentHistoryResponse;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentHistoryQueryRepository;
import com.gnagnoohc.scms.global.common.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentHistoryServiceTest {

    @Mock
    AssessmentHistoryQueryRepository assessmentHistoryQueryRepository;

    @InjectMocks
    AssessmentHistoryService assessmentHistoryService;

    @Test
    void getHistory_passesStudentIdKeywordAndPageableThroughToQueryRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        AssessmentHistoryResponse row = new AssessmentHistoryResponse(
                1, 10, "2026학년도 1학기 사전진단", 2026, "SPRING", "PRE", Instant.now());
        when(assessmentHistoryQueryRepository.findHistory(1, "사전", pageable))
                .thenReturn(new PageImpl<>(List.of(row), pageable, 1));

        PageResponse<AssessmentHistoryResponse> response = assessmentHistoryService.getHistory(1, "사전", pageable);

        assertThat(response.content()).containsExactly(row);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void getHistory_whenNoAttempts_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        when(assessmentHistoryQueryRepository.findHistory(1, null, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        PageResponse<AssessmentHistoryResponse> response = assessmentHistoryService.getHistory(1, null, pageable);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }
}
