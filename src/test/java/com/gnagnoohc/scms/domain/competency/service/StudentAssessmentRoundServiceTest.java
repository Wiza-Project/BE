package com.gnagnoohc.scms.domain.competency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnagnoohc.scms.domain.competency.dto.response.StudentAssessmentRoundResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.domain.competency.repository.StudentAssessmentRoundQueryRepository;
import com.gnagnoohc.scms.domain.competency.support.TargetConditionInterpreter;
import com.gnagnoohc.scms.domain.competency.support.TargetConditionInterpreter.StudentTargetSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 응시 가능 회차 목록의 대상자 필터. TargetConditionInterpreter는 실물을 쓴다 —
 * 이 서비스의 핵심이 "재학 여부 판정과 target_condition 판정을 어떤 순서로 조합하는가"라서,
 * 해석기를 목으로 바꾸면 정작 검증하려는 조합 규칙이 스텁으로 덮인다.
 */
@ExtendWith(MockitoExtension.class)
class StudentAssessmentRoundServiceTest {

    private static final Integer STUDENT_ID = 1;

    @Mock AssessmentRoundRepository assessmentRoundRepository;
    @Mock AssessmentRoundQuestionRepository assessmentRoundQuestionRepository;
    @Mock AssessmentAttemptRepository assessmentAttemptRepository;
    @Mock StudentAssessmentRoundQueryRepository studentAssessmentRoundQueryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StudentAssessmentRoundService service() {
        return new StudentAssessmentRoundService(
                assessmentRoundRepository,
                assessmentRoundQuestionRepository,
                assessmentAttemptRepository,
                studentAssessmentRoundQueryRepository,
                new TargetConditionInterpreter());
    }

    private AssessmentRound buildOpenRound(Integer roundId, Object targetCondition) {
        Instant startsAt = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant endsAt = Instant.now().plus(7, ChronoUnit.DAYS);
        AssessmentRound round = AssessmentRound.create(
                "2026학년도 1학기 사전진단", 2026, "SPRING", "PRE", startsAt, endsAt,
                targetCondition == null ? null : objectMapper.valueToTree(targetCondition), 1);
        ReflectionTestUtils.setField(round, "assessmentRoundId", roundId);
        return round;
    }

    private void givenOpenRounds(AssessmentRound... rounds) {
        when(assessmentRoundRepository.findByStartsAtLessThanEqualAndEndsAtGreaterThanEqualOrderByStartsAtDesc(
                any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(rounds));
    }

    private AssessmentAttempt buildAttempt(AssessmentRound round, Integer attemptId, boolean submitted) {
        AssessmentAttempt attempt = AssessmentAttempt.create(round, null, null);
        ReflectionTestUtils.setField(attempt, "attemptId", attemptId);
        if (submitted) {
            ReflectionTestUtils.setField(attempt, "submittedAt", Instant.now());
            ReflectionTestUtils.setField(attempt, "attemptStatus", "SUBMITTED");
        } else {
            ReflectionTestUtils.setField(attempt, "attemptStatus", "IN_PROGRESS");
        }
        return attempt;
    }

    private void givenMyAttempts(List<Integer> roundIds, AssessmentAttempt... attempts) {
        when(assessmentAttemptRepository.findByAssessmentRound_AssessmentRoundIdInAndStudent_UserId(
                roundIds, STUDENT_ID))
                .thenReturn(List.of(attempts));
    }

    /**
     * 이 테스트가 잡는 회귀: 재학 여부를 matches()에 맡기면 target_condition이 없는(=전체 학생 대상)
     * 회차는 무조건 true로 통과해서, 학년·전공 조건이 걸린 회차만 걸러지고 전체 대상 회차는
     * 비재학생에게 그대로 노출된다.
     */
    @Test
    void getOpenRounds_whenNotEnrolledWithNoInProgressAttempt_returnsEmptyEvenForRoundWithoutTargetCondition() {
        givenOpenRounds(buildOpenRound(100, null));
        when(studentAssessmentRoundQueryRepository.loadTargetSnapshot(STUDENT_ID))
                .thenReturn(StudentTargetSnapshot.notEnrolled());
        givenMyAttempts(List.of(100));

        List<StudentAssessmentRoundResponse> result = service().getOpenRounds(STUDENT_ID);

        assertThat(result).isEmpty();
        // 진행 중 attempt가 없으면 문항 수 조회까지 갈 이유가 없다.
        verify(assessmentRoundQuestionRepository, never()).countGroupedByAssessmentRoundIds(anyList());
    }

    @Test
    void getOpenRounds_whenNotEnrolledWithNoInProgressAttempt_returnsEmptyForRoundWithMatchingTargetCondition() {
        givenOpenRounds(buildOpenRound(100, Map.of("grades", List.of(3))));
        when(studentAssessmentRoundQueryRepository.loadTargetSnapshot(STUDENT_ID))
                .thenReturn(StudentTargetSnapshot.notEnrolled());
        givenMyAttempts(List.of(100));

        assertThat(service().getOpenRounds(STUDENT_ID)).isEmpty();
    }

    // 재학 중 시작해둔 미제출 attempt가 있으면, 학적이 바뀌어도 그 회차는 목록에 남겨 이어하기 진입점을 유지한다.
    @Test
    void getOpenRounds_whenNotEnrolledButHasUnsubmittedAttempt_returnsRoundForResume() {
        AssessmentRound round = buildOpenRound(100, null);
        givenOpenRounds(round);
        when(studentAssessmentRoundQueryRepository.loadTargetSnapshot(STUDENT_ID))
                .thenReturn(StudentTargetSnapshot.notEnrolled());
        givenMyAttempts(List.of(100), buildAttempt(round, 500, false));
        when(assessmentRoundQuestionRepository.countGroupedByAssessmentRoundIds(List.of(100)))
                .thenReturn(List.<Object[]>of(new Object[]{100, 90L}));

        List<StudentAssessmentRoundResponse> result = service().getOpenRounds(STUDENT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).assessmentRoundId()).isEqualTo(100);
        assertThat(result.get(0).attemptId()).isEqualTo(500);
        assertThat(result.get(0).attemptStatus()).isEqualTo("IN_PROGRESS");
    }

    // 이미 제출까지 마친 attempt는 이어할 게 없으므로 목록에 되살리지 않는다.
    @Test
    void getOpenRounds_whenNotEnrolledAndAttemptAlreadySubmitted_returnsEmpty() {
        AssessmentRound round = buildOpenRound(100, null);
        givenOpenRounds(round);
        when(studentAssessmentRoundQueryRepository.loadTargetSnapshot(STUDENT_ID))
                .thenReturn(StudentTargetSnapshot.notEnrolled());
        givenMyAttempts(List.of(100), buildAttempt(round, 500, true));

        assertThat(service().getOpenRounds(STUDENT_ID)).isEmpty();
        verify(assessmentRoundQuestionRepository, never()).countGroupedByAssessmentRoundIds(anyList());
    }

    @Test
    void getOpenRounds_whenEnrolledAndTargetConditionMatches_returnsRound() {
        givenOpenRounds(buildOpenRound(100, Map.of("grades", List.of(3))));
        when(studentAssessmentRoundQueryRepository.loadTargetSnapshot(STUDENT_ID))
                .thenReturn(new StudentTargetSnapshot(true, true, 3, 4000));
        when(assessmentRoundQuestionRepository.countGroupedByAssessmentRoundIds(List.of(100)))
                // 명시적 타입 인자 없이 List.of(배열)을 쓰면 varargs로 펼쳐져 List<Object>로 추론된다.
                .thenReturn(List.<Object[]>of(new Object[]{100, 90L}));
        when(assessmentAttemptRepository.findByAssessmentRound_AssessmentRoundIdInAndStudent_UserId(
                List.of(100), STUDENT_ID))
                .thenReturn(List.of());

        List<StudentAssessmentRoundResponse> result = service().getOpenRounds(STUDENT_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).assessmentRoundId()).isEqualTo(100);
        assertThat(result.get(0).questionCount()).isEqualTo(90);
        assertThat(result.get(0).attemptId()).isNull();
    }

    // 재학생이라도 target_condition이 안 맞으면 여전히 제외된다 — 재학 필터가 기존 판정을 덮지 않는다.
    @Test
    void getOpenRounds_whenEnrolledButTargetConditionMismatches_returnsEmpty() {
        givenOpenRounds(buildOpenRound(100, Map.of("grades", List.of(1))));
        when(studentAssessmentRoundQueryRepository.loadTargetSnapshot(STUDENT_ID))
                .thenReturn(new StudentTargetSnapshot(true, true, 3, 4000));

        assertThat(service().getOpenRounds(STUDENT_ID)).isEmpty();
    }

    @Test
    void getOpenRounds_whenNoOpenRound_returnsEmptyWithoutLoadingSnapshot() {
        givenOpenRounds();

        assertThat(service().getOpenRounds(STUDENT_ID)).isEmpty();
        verify(studentAssessmentRoundQueryRepository, never()).loadTargetSnapshot(any());
    }
}
