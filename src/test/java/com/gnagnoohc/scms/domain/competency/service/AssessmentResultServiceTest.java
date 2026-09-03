package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResultResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentScoreRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentResultServiceTest {

    private static final Integer STUDENT_ID = 1;
    private static final Integer ATTEMPT_ID = 10;
    private static final Integer ROUND_ID = 20;

    @Mock
    AssessmentAttemptAccessGuard assessmentAttemptAccessGuard;

    @Mock
    AssessmentScoreRepository assessmentScoreRepository;

    @InjectMocks
    AssessmentResultService assessmentResultService;

    private static <T> T newInstance(Class<T> type) throws ReflectiveOperationException {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static AppUser buildStudent(Integer userId) throws ReflectiveOperationException {
        AppUser student = newInstance(AppUser.class);
        ReflectionTestUtils.setField(student, "userId", userId);
        return student;
    }

    private static AssessmentRound buildRound(boolean percentileCompleted) {
        Instant now = Instant.now();
        AssessmentRound round = AssessmentRound.create(
                "2026학년도 1학기 사전진단", 2026, "SPRING", "PRE",
                now.minus(10, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), null, 1);
        ReflectionTestUtils.setField(round, "assessmentRoundId", ROUND_ID);
        if (percentileCompleted) {
            round.completePercentileCalculation();
        }
        return round;
    }

    private static AssessmentAttempt buildAttempt(AssessmentRound round, AppUser student) throws ReflectiveOperationException {
        AssessmentAttempt attempt = newInstance(AssessmentAttempt.class);
        ReflectionTestUtils.setField(attempt, "attemptId", ATTEMPT_ID);
        ReflectionTestUtils.setField(attempt, "assessmentRound", round);
        ReflectionTestUtils.setField(attempt, "student", student);
        ReflectionTestUtils.setField(attempt, "submittedAt", Instant.now());
        return attempt;
    }

    private static Competency buildCompetency(Integer competencyId, String code, String name, Integer displayOrder) {
        Competency competency = Competency.createTop(code, name, "English", "설명", displayOrder, 1);
        ReflectionTestUtils.setField(competency, "competencyId", competencyId);
        return competency;
    }

    private static AssessmentScore buildScore(AssessmentAttempt attempt, Competency competency,
                                               BigDecimal convertedScore, BigDecimal percentile) {
        AssessmentScore score = AssessmentScore.create(attempt, competency, BigDecimal.valueOf(3), convertedScore);
        if (percentile != null) {
            score.applyPercentile(percentile);
        }
        return score;
    }

    @Test
    void getResult_whenRoundCompleted_includesPercentileAndOverallAverage() throws Exception {
        AssessmentRound round = buildRound(true);
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        Competency c1 = buildCompetency(100, "C1", "문제해결역량", 1);
        Competency c2 = buildCompetency(200, "C2", "의사소통역량", 2);
        AssessmentScore s1 = buildScore(attempt, c1, BigDecimal.valueOf(60), BigDecimal.valueOf(70));
        AssessmentScore s2 = buildScore(attempt, c2, BigDecimal.valueOf(80), BigDecimal.valueOf(90));

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        when(assessmentScoreRepository.findByAttemptIdFetchCompetencyOrderByDisplayOrder(ATTEMPT_ID))
                .thenReturn(List.of(s1, s2));

        AssessmentResultResponse result = assessmentResultService.getResult(ATTEMPT_ID, STUDENT_ID);

        assertThat(result.attemptId()).isEqualTo(ATTEMPT_ID);
        assertThat(result.roundId()).isEqualTo(ROUND_ID);
        assertThat(result.percentileAvailable()).isTrue();
        assertThat(result.overallAverageScore()).isEqualByComparingTo(BigDecimal.valueOf(70)); // (60+80)/2
        assertThat(result.scores()).hasSize(2);
        assertThat(result.scores().get(0).percentile()).isEqualByComparingTo(BigDecimal.valueOf(70));
        assertThat(result.scores().get(1).percentile()).isEqualByComparingTo(BigDecimal.valueOf(90));
    }

    // 백분위는 DB에 값이 남아있어도(과거 계산분 등) 회차가 아직 COMPLETED가 아니면 응답에서 숨겨야 한다.
    @Test
    void getResult_whenRoundNotCompleted_hidesPercentileEvenIfValuePresent() throws Exception {
        AssessmentRound round = buildRound(false);
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        Competency c1 = buildCompetency(100, "C1", "문제해결역량", 1);
        AssessmentScore s1 = buildScore(attempt, c1, BigDecimal.valueOf(60), BigDecimal.valueOf(70));

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        when(assessmentScoreRepository.findByAttemptIdFetchCompetencyOrderByDisplayOrder(ATTEMPT_ID))
                .thenReturn(List.of(s1));

        AssessmentResultResponse result = assessmentResultService.getResult(ATTEMPT_ID, STUDENT_ID);

        assertThat(result.percentileAvailable()).isFalse();
        assertThat(result.scores().get(0).percentile()).isNull();
        assertThat(result.overallAverageScore()).isEqualByComparingTo(BigDecimal.valueOf(60));
    }

    @Test
    void getResult_whenNoScoresYet_throwsResultNotAvailable() throws Exception {
        AssessmentRound round = buildRound(false);
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        when(assessmentScoreRepository.findByAttemptIdFetchCompetencyOrderByDisplayOrder(ATTEMPT_ID))
                .thenReturn(List.of());

        assertThatThrownBy(() -> assessmentResultService.getResult(ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESULT_NOT_AVAILABLE);
    }

    @Test
    void getResult_whenAttemptNotOwnedByStudent_propagatesAssessmentAttemptNotFound() {
        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID))
                .thenThrow(new BusinessException(ErrorCode.ASSESSMENT_ATTEMPT_NOT_FOUND));

        assertThatThrownBy(() -> assessmentResultService.getResult(ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_ATTEMPT_NOT_FOUND);
    }
}
