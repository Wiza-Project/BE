package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentComparisonResponse;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResultResponse;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResultResponse.CompetencyResult;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentComparisonServiceTest {

    private static final Integer STUDENT_ID = 1;
    private static final Integer PRE_ATTEMPT_ID = 10;
    private static final Integer POST_ATTEMPT_ID = 20;

    @Mock
    AssessmentResultService assessmentResultService;

    @Mock
    AssessmentAttemptAccessGuard assessmentAttemptAccessGuard;

    @InjectMocks
    AssessmentComparisonService assessmentComparisonService;

    private static <T> T newInstance(Class<T> type) throws ReflectiveOperationException {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static AssessmentRound buildRound(int roundId, String assessmentType, int academicYear) {
        Instant now = Instant.now();
        AssessmentRound round = AssessmentRound.create(
                assessmentType.equals("PRE") ? academicYear + "학년도 사전진단" : academicYear + "학년도 사후진단",
                academicYear, "SPRING", assessmentType,
                now.minus(30, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS), null, 1);
        ReflectionTestUtils.setField(round, "assessmentRoundId", roundId);
        return round;
    }

    private static AssessmentRound buildRound(int roundId, String assessmentType) {
        return buildRound(roundId, assessmentType, 2026);
    }

    private static AssessmentAttempt buildAttempt(int attemptId, AssessmentRound round) throws ReflectiveOperationException {
        AssessmentAttempt attempt = newInstance(AssessmentAttempt.class);
        ReflectionTestUtils.setField(attempt, "attemptId", attemptId);
        ReflectionTestUtils.setField(attempt, "assessmentRound", round);
        return attempt;
    }

    private static AssessmentResultResponse resultOf(int attemptId, int roundId, Instant submittedAt,
                                                     BigDecimal overallAverage, List<CompetencyResult> scores) {
        return new AssessmentResultResponse(attemptId, roundId, submittedAt, overallAverage, true, scores);
    }

    private static CompetencyResult score(int competencyId, String name, int displayOrder, double converted) {
        return new CompetencyResult(competencyId, name, displayOrder, BigDecimal.valueOf(converted), null);
    }

    private void stubSide(Integer attemptId, AssessmentResultResponse result, AssessmentAttempt attempt) {
        when(assessmentResultService.getResult(attemptId, STUDENT_ID)).thenReturn(result);
        when(assessmentAttemptAccessGuard.getOwnAttempt(attemptId, STUDENT_ID)).thenReturn(attempt);
    }

    @Test
    void compare_ordersPreBeforePost_regardlessOfArgumentOrder_andComputesDeltas() throws Exception {
        Instant preSubmitted = Instant.now().minus(20, ChronoUnit.DAYS);
        Instant postSubmitted = Instant.now().minus(2, ChronoUnit.DAYS);
        AssessmentResultResponse preResult = resultOf(PRE_ATTEMPT_ID, 100, preSubmitted, BigDecimal.valueOf(70),
                List.of(score(1, "문제해결역량", 1, 60), score(2, "의사소통역량", 2, 80)));
        AssessmentResultResponse postResult = resultOf(POST_ATTEMPT_ID, 200, postSubmitted, BigDecimal.valueOf(72.5),
                List.of(score(1, "문제해결역량", 1, 75), score(2, "의사소통역량", 2, 70)));
        stubSide(PRE_ATTEMPT_ID, preResult, buildAttempt(PRE_ATTEMPT_ID, buildRound(100, "PRE")));
        stubSide(POST_ATTEMPT_ID, postResult, buildAttempt(POST_ATTEMPT_ID, buildRound(200, "POST")));

        // 인자 순서를 사후 먼저 넘겨도 응답은 사전 → 사후로 정렬돼야 한다.
        AssessmentComparisonResponse response =
                assessmentComparisonService.compare(POST_ATTEMPT_ID, PRE_ATTEMPT_ID, STUDENT_ID);

        assertThat(response.before().attemptId()).isEqualTo(PRE_ATTEMPT_ID);
        assertThat(response.before().assessmentType()).isEqualTo("PRE");
        assertThat(response.after().attemptId()).isEqualTo(POST_ATTEMPT_ID);
        assertThat(response.after().assessmentType()).isEqualTo("POST");
        assertThat(response.before().overallAverageScore()).isEqualByComparingTo(BigDecimal.valueOf(70));

        assertThat(response.deltas()).hasSize(2);
        assertThat(response.deltas().get(0).competencyId()).isEqualTo(1);
        assertThat(response.deltas().get(0).beforeScore()).isEqualByComparingTo(BigDecimal.valueOf(60));
        assertThat(response.deltas().get(0).afterScore()).isEqualByComparingTo(BigDecimal.valueOf(75));
        assertThat(response.deltas().get(0).delta()).isEqualByComparingTo(BigDecimal.valueOf(15));
        // 떨어진 역량도 마스킹 없이 음수 그대로.
        assertThat(response.deltas().get(1).delta()).isEqualByComparingTo(BigDecimal.valueOf(-10));
    }

    @Test
    void compare_whenBothSameType_throwsNotPrePostPair() throws Exception {
        AssessmentResultResponse firstResult = resultOf(PRE_ATTEMPT_ID, 100, Instant.now().minus(20, ChronoUnit.DAYS),
                BigDecimal.valueOf(50), List.of(score(1, "문제해결역량", 1, 50)));
        AssessmentResultResponse secondResult = resultOf(POST_ATTEMPT_ID, 200, Instant.now().minus(3, ChronoUnit.DAYS),
                BigDecimal.valueOf(65), List.of(score(1, "문제해결역량", 1, 65)));
        stubSide(PRE_ATTEMPT_ID, firstResult, buildAttempt(PRE_ATTEMPT_ID, buildRound(100, "PRE", 2025)));
        stubSide(POST_ATTEMPT_ID, secondResult, buildAttempt(POST_ATTEMPT_ID, buildRound(200, "PRE", 2026)));

        assertThatThrownBy(() -> assessmentComparisonService.compare(PRE_ATTEMPT_ID, POST_ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_COMPARISON_NOT_PRE_POST_PAIR);
    }

    @Test
    void compare_whenAcademicYearDiffers_throwsNotPrePostPair() throws Exception {
        AssessmentResultResponse preResult = resultOf(PRE_ATTEMPT_ID, 100, Instant.now().minus(400, ChronoUnit.DAYS),
                BigDecimal.valueOf(50), List.of(score(1, "문제해결역량", 1, 50)));
        AssessmentResultResponse postResult = resultOf(POST_ATTEMPT_ID, 200, Instant.now().minus(3, ChronoUnit.DAYS),
                BigDecimal.valueOf(65), List.of(score(1, "문제해결역량", 1, 65)));
        stubSide(PRE_ATTEMPT_ID, preResult, buildAttempt(PRE_ATTEMPT_ID, buildRound(100, "PRE", 2025)));
        stubSide(POST_ATTEMPT_ID, postResult, buildAttempt(POST_ATTEMPT_ID, buildRound(200, "POST", 2026)));

        assertThatThrownBy(() -> assessmentComparisonService.compare(PRE_ATTEMPT_ID, POST_ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_COMPARISON_NOT_PRE_POST_PAIR);
    }

    // 한쪽에만 있는 역량(데이터 이상)이 생겨도 deltas가 방사형 차트 축순서(displayOrder)를 따라야 한다.
    @Test
    void compare_whenCompetencySetsDiffer_ordersDeltasByDisplayOrder() throws Exception {
        AssessmentResultResponse preResult = resultOf(PRE_ATTEMPT_ID, 100, Instant.now().minus(20, ChronoUnit.DAYS),
                BigDecimal.valueOf(60), List.of(score(1, "문제해결역량", 1, 60), score(3, "대인관계역량", 3, 40)));
        AssessmentResultResponse postResult = resultOf(POST_ATTEMPT_ID, 200, Instant.now().minus(2, ChronoUnit.DAYS),
                BigDecimal.valueOf(70), List.of(
                        score(1, "문제해결역량", 1, 66),
                        score(2, "의사소통역량", 2, 50),
                        score(3, "대인관계역량", 3, 44)));
        stubSide(PRE_ATTEMPT_ID, preResult, buildAttempt(PRE_ATTEMPT_ID, buildRound(100, "PRE")));
        stubSide(POST_ATTEMPT_ID, postResult, buildAttempt(POST_ATTEMPT_ID, buildRound(200, "POST")));

        AssessmentComparisonResponse response =
                assessmentComparisonService.compare(PRE_ATTEMPT_ID, POST_ATTEMPT_ID, STUDENT_ID);

        assertThat(response.deltas())
                .extracting(d -> d.displayOrder())
                .containsExactly(1, 2, 3);
        // after 전용 역량(displayOrder=2)은 목록 끝이 아니라 축순서대로 가운데에 위치한다.
        assertThat(response.deltas().get(1).competencyId()).isEqualTo(2);
        assertThat(response.deltas().get(1).beforeScore()).isNull();
        assertThat(response.deltas().get(1).delta()).isNull();
        assertThat(response.deltas().get(2).delta()).isEqualByComparingTo(BigDecimal.valueOf(4));
    }

    @Test
    void compare_whenSameAttemptIdTwice_throwsSameAttempt() {
        assertThatThrownBy(() -> assessmentComparisonService.compare(PRE_ATTEMPT_ID, PRE_ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_COMPARISON_SAME_ATTEMPT);
    }

    @Test
    void compare_whenOneSideNotScored_propagatesResultNotAvailable() {
        lenient().when(assessmentResultService.getResult(PRE_ATTEMPT_ID, STUDENT_ID))
                .thenThrow(new BusinessException(ErrorCode.RESULT_NOT_AVAILABLE));

        assertThatThrownBy(() -> assessmentComparisonService.compare(PRE_ATTEMPT_ID, POST_ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESULT_NOT_AVAILABLE);
    }
}
