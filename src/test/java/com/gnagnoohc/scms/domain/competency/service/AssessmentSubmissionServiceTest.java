package com.gnagnoohc.scms.domain.competency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentSubmitResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentQuestion;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRoundQuestion;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRoundQuestionId;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentResponseRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentScoreRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssessmentSubmissionServiceTest {

    private static final Integer STUDENT_ID = 1;
    private static final Integer ATTEMPT_ID = 10;
    private static final Integer ROUND_ID = 20;
    private static final Integer QUESTION_ID = 30;

    @Mock
    AssessmentAttemptAccessGuard assessmentAttemptAccessGuard;

    @Mock
    AssessmentResponseRepository assessmentResponseRepository;

    @Mock
    AssessmentRoundQuestionRepository assessmentRoundQuestionRepository;

    @Mock
    AssessmentRoundRepository assessmentRoundRepository;

    @Mock
    AssessmentScoreRepository assessmentScoreRepository;

    // AssessmentScoreCalculator는 리포지토리 의존성 없는 순수 계산 컴포넌트라 목(mock) 대신 실제 인스턴스를 사용한다
    // (Mockito @InjectMocks는 @Mock이 아닌 협력자를 채워주지 못해 생성자로 직접 조립한다).
    AssessmentSubmissionService assessmentSubmissionService;

    @BeforeEach
    void setUp() {
        assessmentSubmissionService = new AssessmentSubmissionService(
                assessmentAttemptAccessGuard, assessmentResponseRepository,
                assessmentRoundQuestionRepository, assessmentRoundRepository, assessmentScoreRepository,
                new AssessmentScoreCalculator());
    }

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

    private static AssessmentRound buildRound(Instant startsAt, Instant endsAt) {
        AssessmentRound round = AssessmentRound.create(
                "2026학년도 1학기 사전진단", 2026, "SPRING", "PRE", startsAt, endsAt, null, 1);
        ReflectionTestUtils.setField(round, "assessmentRoundId", ROUND_ID);
        return round;
    }

    private static AssessmentAttempt buildAttempt(AssessmentRound round, AppUser student) throws ReflectiveOperationException {
        AssessmentAttempt attempt = newInstance(AssessmentAttempt.class);
        ReflectionTestUtils.setField(attempt, "attemptId", ATTEMPT_ID);
        ReflectionTestUtils.setField(attempt, "assessmentRound", round);
        ReflectionTestUtils.setField(attempt, "student", student);
        return attempt;
    }

    private static Competency buildCompetency(Integer competencyId) {
        return buildCompetency(competencyId, "C1", "문제해결역량", 1);
    }

    private static Competency buildCompetency(Integer competencyId, String code, String name, Integer displayOrder) {
        Competency competency = Competency.createTop(code, name, "Problem Solving", "설명", displayOrder, 1);
        ReflectionTestUtils.setField(competency, "competencyId", competencyId);
        return competency;
    }

    private static AssessmentQuestion buildQuestion(Competency competency, Integer questionId) {
        return buildQuestion(competency, questionId, false);
    }

    private static AssessmentQuestion buildQuestion(Competency competency, Integer questionId, boolean reverse) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode options = mapper.createArrayNode();
        for (int value = 1; value <= 5; value++) {
            ObjectNode option = mapper.createObjectNode();
            option.put("value", value);
            option.put("label", value + "점");
            options.add(option);
        }
        AssessmentQuestion question = AssessmentQuestion.createFromUpload(competency, "나는 새로운 문제를 잘 해결한다.", options, 1);
        ReflectionTestUtils.setField(question, "questionId", questionId);
        question.editInPlace(question.getQuestionText(), options, reverse);
        return question;
    }

    private static AssessmentRoundQuestion buildRoundQuestion(AssessmentRound round, AssessmentQuestion question, int displayOrder)
            throws ReflectiveOperationException {
        AssessmentRoundQuestionId id = newInstance(AssessmentRoundQuestionId.class);
        ReflectionTestUtils.setField(id, "assessmentRoundId", round.getAssessmentRoundId());
        ReflectionTestUtils.setField(id, "questionId", question.getQuestionId());

        AssessmentRoundQuestion roundQuestion = newInstance(AssessmentRoundQuestion.class);
        ReflectionTestUtils.setField(roundQuestion, "id", id);
        ReflectionTestUtils.setField(roundQuestion, "assessmentRound", round);
        ReflectionTestUtils.setField(roundQuestion, "question", question);
        ReflectionTestUtils.setField(roundQuestion, "displayOrder", displayOrder);
        ReflectionTestUtils.setField(roundQuestion, "createdBy", 1);
        return roundQuestion;
    }

    @Test
    void submit_allAnswered_marksScoredAndSavesScores() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        Competency competency = buildCompetency(100);
        AssessmentQuestion question1 = buildQuestion(competency, QUESTION_ID);
        AssessmentQuestion question2 = buildQuestion(competency, QUESTION_ID + 1);
        AssessmentRoundQuestion rq1 = buildRoundQuestion(round, question1, 1);
        AssessmentRoundQuestion rq2 = buildRoundQuestion(round, question2, 2);

        AssessmentResponse response1 = AssessmentResponse.create(attempt, question1, BigDecimal.valueOf(4), STUDENT_ID);
        AssessmentResponse response2 = AssessmentResponse.create(attempt, question2, BigDecimal.valueOf(2), STUDENT_ID);

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        when(assessmentRoundQuestionRepository.findByAssessmentRound_AssessmentRoundIdOrderByDisplayOrderAsc(ROUND_ID))
                .thenReturn(List.of(rq1, rq2));
        when(assessmentResponseRepository.findByAttempt_AttemptId(ATTEMPT_ID))
                .thenReturn(List.of(response1, response2));
        when(assessmentRoundRepository.findByIdForUpdate(ROUND_ID)).thenReturn(Optional.of(round));
        when(assessmentScoreRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentSubmitResponse result = assessmentSubmissionService.submit(ATTEMPT_ID, STUDENT_ID);

        assertThat(attempt.getAttemptStatus()).isEqualTo("SCORED");
        assertThat(attempt.getSubmittedAt()).isNotNull();
        assertThat(result.attemptId()).isEqualTo(ATTEMPT_ID);
        assertThat(result.attemptStatus()).isEqualTo("SCORED");
        assertThat(result.scores()).hasSize(1);
        assertThat(result.scores().get(0).rawScore()).isEqualByComparingTo(BigDecimal.valueOf(3)); // (4+2)/2
        assertThat(result.scores().get(0).convertedScore()).isEqualByComparingTo(BigDecimal.valueOf(60)); // 3/5*100
    }

    // 역량 2개(하나는 역문항 포함, 하나는 정문항만)를 함께 제출하는 submit() 전체 경로를 검증한다.
    // competencyId와 displayOrder 순서를 일부러 반대로 둬서, 결과 순서가 competencyId나 groupingBy의
    // 내부 순회 순서가 아니라 실제로 Competency.displayOrder를 따르는지까지 함께 확인한다.
    @Test
    void submit_multipleCompetenciesWithReverseQuestion_calculatesEachCompetencyAndOrdersByDisplayOrder() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));

        Competency competencyDisplayedSecond = buildCompetency(100, "C1", "문제해결역량", 2);
        Competency competencyDisplayedFirst = buildCompetency(200, "C2", "의사소통역량", 1);

        AssessmentQuestion q1 = buildQuestion(competencyDisplayedSecond, QUESTION_ID, false);
        AssessmentQuestion q2 = buildQuestion(competencyDisplayedSecond, QUESTION_ID + 1, false);
        AssessmentQuestion q3 = buildQuestion(competencyDisplayedFirst, QUESTION_ID + 2, true); // 역문항

        AssessmentRoundQuestion rq1 = buildRoundQuestion(round, q1, 1);
        AssessmentRoundQuestion rq2 = buildRoundQuestion(round, q2, 2);
        AssessmentRoundQuestion rq3 = buildRoundQuestion(round, q3, 3);

        AssessmentResponse response1 = AssessmentResponse.create(attempt, q1, BigDecimal.valueOf(4), STUDENT_ID);
        AssessmentResponse response2 = AssessmentResponse.create(attempt, q2, BigDecimal.valueOf(2), STUDENT_ID);
        AssessmentResponse response3 = AssessmentResponse.create(attempt, q3, BigDecimal.valueOf(2), STUDENT_ID); // 6-2=4로 역산되어야 함

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        when(assessmentRoundQuestionRepository.findByAssessmentRound_AssessmentRoundIdOrderByDisplayOrderAsc(ROUND_ID))
                .thenReturn(List.of(rq1, rq2, rq3));
        when(assessmentResponseRepository.findByAttempt_AttemptId(ATTEMPT_ID))
                .thenReturn(List.of(response1, response2, response3));
        when(assessmentRoundRepository.findByIdForUpdate(ROUND_ID)).thenReturn(Optional.of(round));
        when(assessmentScoreRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AssessmentSubmitResponse result = assessmentSubmissionService.submit(ATTEMPT_ID, STUDENT_ID);

        assertThat(attempt.getAttemptStatus()).isEqualTo("SCORED");
        assertThat(result.scores()).hasSize(2);
        assertThat(result.scores()).extracting(AssessmentSubmitResponse.CompetencyScore::competencyId)
                .containsExactly(200, 100); // displayOrder 1인 200번 역량이 먼저 와야 한다

        AssessmentSubmitResponse.CompetencyScore reverseCompetencyScore = result.scores().get(0);
        assertThat(reverseCompetencyScore.rawScore()).isEqualByComparingTo(BigDecimal.valueOf(4)); // 6-2
        assertThat(reverseCompetencyScore.convertedScore()).isEqualByComparingTo(BigDecimal.valueOf(80)); // 4/5*100

        AssessmentSubmitResponse.CompetencyScore normalCompetencyScore = result.scores().get(1);
        assertThat(normalCompetencyScore.rawScore()).isEqualByComparingTo(BigDecimal.valueOf(3)); // (4+2)/2
        assertThat(normalCompetencyScore.convertedScore()).isEqualByComparingTo(BigDecimal.valueOf(60)); // 3/5*100
    }

    @Test
    void submit_whenIncompleteAnswers_throwsIncompleteAnswerWithMissingQuestionIds() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        Competency competency = buildCompetency(100);
        AssessmentQuestion question1 = buildQuestion(competency, QUESTION_ID);
        AssessmentQuestion question2 = buildQuestion(competency, QUESTION_ID + 1);
        AssessmentRoundQuestion rq1 = buildRoundQuestion(round, question1, 1);
        AssessmentRoundQuestion rq2 = buildRoundQuestion(round, question2, 2);

        AssessmentResponse response1 = AssessmentResponse.create(attempt, question1, BigDecimal.valueOf(4), STUDENT_ID);

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        when(assessmentRoundQuestionRepository.findByAssessmentRound_AssessmentRoundIdOrderByDisplayOrderAsc(ROUND_ID))
                .thenReturn(List.of(rq1, rq2));
        when(assessmentResponseRepository.findByAttempt_AttemptId(ATTEMPT_ID))
                .thenReturn(List.of(response1));

        assertThatThrownBy(() -> assessmentSubmissionService.submit(ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.INCOMPLETE_ANSWER);
                    assertThat(be.getData()).isEqualTo(List.of(QUESTION_ID + 1));
                });

        // 미응답 상태에서 예외가 나야 하며 제출 처리(submit/markScored)가 진행되면 안 된다.
        assertThat(attempt.getSubmittedAt()).isNull();
        assertThat(attempt.getAttemptStatus()).isEqualTo("NOT_STARTED");
    }

    // 소유권·제출완료·기간 검증 자체(각 조건에서 어떤 에러코드가 나오는지)는
    // AssessmentAttemptAccessGuardTest에서 직접 검증한다. 여기서는 submit이 guard 결과를
    // 그대로 전파하는지만 확인한다.
    @Test
    void submit_whenAlreadySubmitted_throwsDiagnosisAlreadySubmitted() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        doThrow(new BusinessException(ErrorCode.DIAGNOSIS_ALREADY_SUBMITTED))
                .when(assessmentAttemptAccessGuard).assertNotSubmitted(attempt);

        assertThatThrownBy(() -> assessmentSubmissionService.submit(ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIAGNOSIS_ALREADY_SUBMITTED);
    }

    @Test
    void submit_whenPeriodClosed_throwsDiagnosisPeriodClosed() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(10, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        doThrow(new BusinessException(ErrorCode.DIAGNOSIS_PERIOD_CLOSED))
                .when(assessmentAttemptAccessGuard).assertPeriodOpen(attempt);

        assertThatThrownBy(() -> assessmentSubmissionService.submit(ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIAGNOSIS_PERIOD_CLOSED);
    }

    // 배치(AssessmentPercentileBatchService)가 이 회차의 잠금을 먼저 잡고 COMPLETED로 확정한 뒤에야
    // 이 제출이 잠금을 얻어 재개되는 경합 상황을 재현한다. 최초 assertPeriodOpen(38번 줄) 시점엔 기간
    // 안이었더라도, 잠금 획득 후 재검증에서 걸러져야 하고 saveAll이 호출되면 안 된다.
    @Test
    void submit_whenRoundAlreadyCompletedByBatch_throwsDiagnosisPeriodClosedWithoutSaving() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        round.completePercentileCalculation();
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        Competency competency = buildCompetency(100);
        AssessmentQuestion question = buildQuestion(competency, QUESTION_ID);
        AssessmentRoundQuestion rq = buildRoundQuestion(round, question, 1);
        AssessmentResponse response = AssessmentResponse.create(attempt, question, BigDecimal.valueOf(4), STUDENT_ID);

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        when(assessmentRoundQuestionRepository.findByAssessmentRound_AssessmentRoundIdOrderByDisplayOrderAsc(ROUND_ID))
                .thenReturn(List.of(rq));
        when(assessmentResponseRepository.findByAttempt_AttemptId(ATTEMPT_ID)).thenReturn(List.of(response));
        when(assessmentRoundRepository.findByIdForUpdate(ROUND_ID)).thenReturn(Optional.of(round));

        assertThatThrownBy(() -> assessmentSubmissionService.submit(ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIAGNOSIS_PERIOD_CLOSED);

        verify(assessmentScoreRepository, never()).saveAll(any());
        assertThat(attempt.getAttemptStatus()).isEqualTo("NOT_STARTED");
    }

    @Test
    void submit_whenAttemptNotOwnedByStudent_throwsAssessmentAttemptNotFound() {
        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID))
                .thenThrow(new BusinessException(ErrorCode.ASSESSMENT_ATTEMPT_NOT_FOUND));

        assertThatThrownBy(() -> assessmentSubmissionService.submit(ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_ATTEMPT_NOT_FOUND);
    }

    @Test
    void submit_whenAttemptNotFound_throwsAssessmentAttemptNotFound() {
        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID))
                .thenThrow(new BusinessException(ErrorCode.ASSESSMENT_ATTEMPT_NOT_FOUND));

        assertThatThrownBy(() -> assessmentSubmissionService.submit(ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_ATTEMPT_NOT_FOUND);
    }

    // 두 제출 요청이 거의 동시에 들어와 둘 다 assertNotSubmitted를 통과한 뒤 점수 저장을 시도하는 상황을 재현.
    // 먼저 커밋된 쪽은 성공하고, 나중 쪽은 uq_assessment_score_attempt_competency 위반으로 saveAll이 실패해야 한다.
    @Test
    void submit_whenConcurrentSubmitViolatesScoreUniqueConstraint_throwsDiagnosisAlreadySubmitted() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        Competency competency = buildCompetency(100);
        AssessmentQuestion question = buildQuestion(competency, QUESTION_ID);
        AssessmentRoundQuestion rq = buildRoundQuestion(round, question, 1);
        AssessmentResponse response = AssessmentResponse.create(attempt, question, BigDecimal.valueOf(4), STUDENT_ID);

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        when(assessmentRoundQuestionRepository.findByAssessmentRound_AssessmentRoundIdOrderByDisplayOrderAsc(ROUND_ID))
                .thenReturn(List.of(rq));
        when(assessmentResponseRepository.findByAttempt_AttemptId(ATTEMPT_ID)).thenReturn(List.of(response));
        when(assessmentRoundRepository.findByIdForUpdate(ROUND_ID)).thenReturn(Optional.of(round));
        when(assessmentScoreRepository.saveAll(any()))
                .thenThrow(new DataIntegrityViolationException("uq_assessment_score_attempt_competency"));

        assertThatThrownBy(() -> assessmentSubmissionService.submit(ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIAGNOSIS_ALREADY_SUBMITTED);
    }
}
