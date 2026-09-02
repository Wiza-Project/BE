package com.gnagnoohc.scms.domain.competency.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentAttendanceResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttendanceQueryRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentAttendanceServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    AssessmentRoundRepository assessmentRoundRepository;

    @Mock
    AssessmentAttendanceQueryRepository assessmentAttendanceQueryRepository;

    @InjectMocks
    AssessmentAttendanceService assessmentAttendanceService;

    private AssessmentRound buildRound(JsonNode targetCondition) {
        Instant startsAt = Instant.now();
        return AssessmentRound.create("2026학년도 1학기 사전진단", 2026, "SPRING", "PRE",
                startsAt, startsAt.plusSeconds(3600), targetCondition, 1);
    }

    @Test
    void getAttendance_whenRoundNotFound_throwsAssessmentRoundNotFound() {
        when(assessmentRoundRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assessmentAttendanceService.getAttendance(999))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND);
    }

    @Test
    void getAttendance_calculatesRateFromTargetAndCompletedCounts() {
        JsonNode targetCondition = objectMapper.valueToTree(Map.of("grade", 3));
        AssessmentRound round = buildRound(targetCondition);
        when(assessmentRoundRepository.findById(1)).thenReturn(Optional.of(round));
        when(assessmentAttendanceQueryRepository.countTargetStudents(targetCondition)).thenReturn(80L);
        when(assessmentAttendanceQueryRepository.countCompletedAttempts(1, targetCondition)).thenReturn(20L);

        AssessmentAttendanceResponse response = assessmentAttendanceService.getAttendance(1);

        assertThat(response.assessmentRoundId()).isEqualTo(1);
        assertThat(response.targetCount()).isEqualTo(80L);
        assertThat(response.completedCount()).isEqualTo(20L);
        assertThat(response.attendanceRate()).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    void getAttendance_whenNoTargetStudents_returnsZeroRateWithoutDivisionError() {
        AssessmentRound round = buildRound(null);
        when(assessmentRoundRepository.findById(1)).thenReturn(Optional.of(round));
        when(assessmentAttendanceQueryRepository.countTargetStudents(null)).thenReturn(0L);
        when(assessmentAttendanceQueryRepository.countCompletedAttempts(1, null)).thenReturn(0L);

        AssessmentAttendanceResponse response = assessmentAttendanceService.getAttendance(1);

        assertThat(response.attendanceRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
