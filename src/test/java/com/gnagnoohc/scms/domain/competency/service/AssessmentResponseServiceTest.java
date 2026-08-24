package com.gnagnoohc.scms.domain.competency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentResponseSaveResponse;
import com.gnagnoohc.scms.domain.competency.dto.AssessmentResumeResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentQuestion;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRoundQuestion;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRoundQuestionId;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentResponseRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundQuestionRepository;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
class AssessmentResponseServiceTest {

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
    AssessmentQuestionRepository assessmentQuestionRepository;

    @InjectMocks
    AssessmentResponseService assessmentResponseService;

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

    private static Competency buildCompetency() {
        Competency competency = Competency.createTop("C1", "문제해결역량", "Problem Solving", "설명", 1, 1);
        ReflectionTestUtils.setField(competency, "competencyId", 100);
        return competency;
    }

    private static AssessmentQuestion buildQuestion(Competency competency) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode options = mapper.createArrayNode();
        for (int value = 1; value <= 5; value++) {
            ObjectNode option = mapper.createObjectNode();
            option.put("value", value);
            option.put("label", value + "점");
            options.add(option);
        }
        AssessmentQuestion question = AssessmentQuestion.createFromUpload(competency, "나는 새로운 문제를 잘 해결한다.", options, 1);
        ReflectionTestUtils.setField(question, "questionId", QUESTION_ID);
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
    void saveResponse_firstTime_startsAttemptAndCreatesResponse() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        AssessmentQuestion question = buildQuestion(buildCompetency());

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        when(assessmentRoundQuestionRepository.existsByAssessmentRound_AssessmentRoundIdAndQuestion_QuestionId(ROUND_ID, QUESTION_ID))
                .thenReturn(true);
        when(assessmentQuestionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));
        when(assessmentResponseRepository.findByAttempt_AttemptIdAndQuestion_QuestionId(ATTEMPT_ID, QUESTION_ID))
                .thenReturn(Optional.empty());
        when(assessmentResponseRepository.save(any(AssessmentResponse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(assessmentRoundQuestionRepository.countByAssessmentRound_AssessmentRoundId(ROUND_ID)).thenReturn(15L);
        when(assessmentResponseRepository.countByAttempt_AttemptId(ATTEMPT_ID)).thenReturn(1L);

        assertThat(attempt.getStartedAt()).isNull();

        AssessmentResponseSaveResponse response = assessmentResponseService.saveResponse(
                ATTEMPT_ID, QUESTION_ID, BigDecimal.valueOf(4), STUDENT_ID);

        assertThat(response.selectedValue()).isEqualByComparingTo(BigDecimal.valueOf(4));
        assertThat(response.progress().answeredCount()).isEqualTo(1);
        assertThat(response.progress().totalCount()).isEqualTo(15);
        assertThat(attempt.getStartedAt()).isNotNull();
        assertThat(attempt.getAttemptStatus()).isEqualTo("IN_PROGRESS");
        verify(assessmentResponseRepository).save(any(AssessmentResponse.class));
    }

    // 같은 문항의 최초 저장 요청 2개가 거의 동시에 들어와 둘 다 findBy에서 빈 값을 본 뒤
    // 둘 다 insert를 시도하는 상황(더블클릭/중복 탭/자동저장 재시도)을 재현. 먼저 커밋된 쪽은 성공하고,
    // 나중 쪽은 uq_assessment_response_attempt_question 위반으로 save()가 실패해야 한다.
    // 이때 재조회 후 update로 인메모리 복구를 시도하지 않고 깨끗한 CONFLICT로 변환해서 던지는지 검증한다
    // (Postgres는 제약 위반 이후 같은 트랜잭션의 후속 쿼리를 전부 거부하므로 그 자리에서 복구 시도가 불가능하다).
    @Test
    void saveResponse_whenConcurrentFirstSaveViolatesUniqueConstraint_throwsResponseSaveConflict() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        AssessmentQuestion question = buildQuestion(buildCompetency());

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        when(assessmentRoundQuestionRepository.existsByAssessmentRound_AssessmentRoundIdAndQuestion_QuestionId(ROUND_ID, QUESTION_ID))
                .thenReturn(true);
        when(assessmentQuestionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));
        when(assessmentResponseRepository.findByAttempt_AttemptIdAndQuestion_QuestionId(ATTEMPT_ID, QUESTION_ID))
                .thenReturn(Optional.empty());
        when(assessmentResponseRepository.save(any(AssessmentResponse.class)))
                .thenThrow(new DataIntegrityViolationException("uq_assessment_response_attempt_question"));

        assertThatThrownBy(() -> assessmentResponseService.saveResponse(ATTEMPT_ID, QUESTION_ID, BigDecimal.valueOf(4), STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESPONSE_SAVE_CONFLICT);
    }

    @Test
    void saveResponse_whenAlreadyAnswered_updatesExistingInsteadOfInserting() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        // 이미 응시가 시작된 상태(재저장 시나리오) — start()가 다시 호출돼도 startedAt이 그대로여야 한다.
        Instant startedAt = now.minus(10, ChronoUnit.MINUTES);
        ReflectionTestUtils.setField(attempt, "startedAt", startedAt);
        ReflectionTestUtils.setField(attempt, "attemptStatus", "IN_PROGRESS");

        AssessmentQuestion question = buildQuestion(buildCompetency());
        AssessmentResponse existing = AssessmentResponse.create(attempt, question, BigDecimal.valueOf(2), STUDENT_ID);

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        when(assessmentRoundQuestionRepository.existsByAssessmentRound_AssessmentRoundIdAndQuestion_QuestionId(ROUND_ID, QUESTION_ID))
                .thenReturn(true);
        when(assessmentQuestionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));
        when(assessmentResponseRepository.findByAttempt_AttemptIdAndQuestion_QuestionId(ATTEMPT_ID, QUESTION_ID))
                .thenReturn(Optional.of(existing));
        when(assessmentRoundQuestionRepository.countByAssessmentRound_AssessmentRoundId(ROUND_ID)).thenReturn(15L);
        when(assessmentResponseRepository.countByAttempt_AttemptId(ATTEMPT_ID)).thenReturn(1L);

        AssessmentResponseSaveResponse response = assessmentResponseService.saveResponse(
                ATTEMPT_ID, QUESTION_ID, BigDecimal.valueOf(5), STUDENT_ID);

        assertThat(response.selectedValue()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(existing.getSelectedValue()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(attempt.getStartedAt()).isEqualTo(startedAt);
        verify(assessmentResponseRepository, never()).save(any());
    }

    @Test
    void saveResponse_whenQuestionNotInRound_throwsQuestionNotInRound() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        when(assessmentRoundQuestionRepository.existsByAssessmentRound_AssessmentRoundIdAndQuestion_QuestionId(ROUND_ID, QUESTION_ID))
                .thenReturn(false);

        assertThatThrownBy(() -> assessmentResponseService.saveResponse(ATTEMPT_ID, QUESTION_ID, BigDecimal.valueOf(3), STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.QUESTION_NOT_IN_ROUND);
    }

    @Test
    void saveResponse_whenSelectedValueNotInResponseOptions_throwsInvalidResponseValue() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        AssessmentQuestion question = buildQuestion(buildCompetency());

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        when(assessmentRoundQuestionRepository.existsByAssessmentRound_AssessmentRoundIdAndQuestion_QuestionId(ROUND_ID, QUESTION_ID))
                .thenReturn(true);
        when(assessmentQuestionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(question));

        assertThatThrownBy(() -> assessmentResponseService.saveResponse(ATTEMPT_ID, QUESTION_ID, BigDecimal.valueOf(99), STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_RESPONSE_VALUE);
    }

    // 소유권·제출완료·기간 검증 자체(각 조건에서 어떤 에러코드가 나오는지)는
    // AssessmentAttemptAccessGuardTest에서 직접 검증한다. 여기서는 saveResponse가 guard 결과를
    // 그대로 전파하는지만 확인한다.
    @Test
    void saveResponse_whenPeriodClosed_throwsDiagnosisPeriodClosed() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        doThrow(new BusinessException(ErrorCode.DIAGNOSIS_PERIOD_CLOSED))
                .when(assessmentAttemptAccessGuard).assertPeriodOpen(attempt);

        assertThatThrownBy(() -> assessmentResponseService.saveResponse(ATTEMPT_ID, QUESTION_ID, BigDecimal.valueOf(3), STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIAGNOSIS_PERIOD_CLOSED);
    }

    @Test
    void saveResponse_whenAlreadySubmitted_throwsDiagnosisAlreadySubmitted() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        doThrow(new BusinessException(ErrorCode.DIAGNOSIS_ALREADY_SUBMITTED))
                .when(assessmentAttemptAccessGuard).assertNotSubmitted(attempt);

        assertThatThrownBy(() -> assessmentResponseService.saveResponse(ATTEMPT_ID, QUESTION_ID, BigDecimal.valueOf(3), STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIAGNOSIS_ALREADY_SUBMITTED);
    }

    @Test
    void saveResponse_whenAttemptNotOwnedByStudent_throwsAssessmentAttemptNotFound() {
        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID))
                .thenThrow(new BusinessException(ErrorCode.ASSESSMENT_ATTEMPT_NOT_FOUND));

        assertThatThrownBy(() -> assessmentResponseService.saveResponse(ATTEMPT_ID, QUESTION_ID, BigDecimal.valueOf(3), STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_ATTEMPT_NOT_FOUND);
    }

    @Test
    void saveResponse_whenAttemptNotFound_throwsAssessmentAttemptNotFound() {
        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID))
                .thenThrow(new BusinessException(ErrorCode.ASSESSMENT_ATTEMPT_NOT_FOUND));

        assertThatThrownBy(() -> assessmentResponseService.saveResponse(ATTEMPT_ID, QUESTION_ID, BigDecimal.valueOf(3), STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_ATTEMPT_NOT_FOUND);
    }

    @Test
    void resume_returnsAllRoundQuestionsWithExistingAnswersAndProgress() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));
        Competency competency = buildCompetency();
        AssessmentQuestion question1 = buildQuestion(competency);
        AssessmentQuestion question2 = buildQuestion(competency);
        ReflectionTestUtils.setField(question2, "questionId", QUESTION_ID + 1);

        AssessmentRoundQuestion rq1 = buildRoundQuestion(round, question1, 1);
        AssessmentRoundQuestion rq2 = buildRoundQuestion(round, question2, 2);

        AssessmentResponse answeredResponse = AssessmentResponse.create(attempt, question1, BigDecimal.valueOf(3), STUDENT_ID);

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        when(assessmentRoundQuestionRepository.findByAssessmentRound_AssessmentRoundIdOrderByDisplayOrderAsc(ROUND_ID))
                .thenReturn(List.of(rq1, rq2));
        when(assessmentResponseRepository.findByAttempt_AttemptId(ATTEMPT_ID))
                .thenReturn(List.of(answeredResponse));

        AssessmentResumeResponse response = assessmentResponseService.resume(ATTEMPT_ID, STUDENT_ID);

        assertThat(response.items()).hasSize(2);
        assertThat(response.progress().answeredCount()).isEqualTo(1);
        assertThat(response.progress().totalCount()).isEqualTo(2);
        assertThat(response.items().get(0).selectedValue()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(response.items().get(1).selectedValue()).isNull();
    }

    @Test
    void resume_whenAlreadySubmitted_throwsDiagnosisAlreadySubmitted() throws Exception {
        Instant now = Instant.now();
        AssessmentRound round = buildRound(now.minus(1, ChronoUnit.DAYS), now.plus(6, ChronoUnit.DAYS));
        AssessmentAttempt attempt = buildAttempt(round, buildStudent(STUDENT_ID));

        when(assessmentAttemptAccessGuard.getOwnAttempt(ATTEMPT_ID, STUDENT_ID)).thenReturn(attempt);
        doThrow(new BusinessException(ErrorCode.DIAGNOSIS_ALREADY_SUBMITTED))
                .when(assessmentAttemptAccessGuard).assertNotSubmitted(attempt);

        assertThatThrownBy(() -> assessmentResponseService.resume(ATTEMPT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DIAGNOSIS_ALREADY_SUBMITTED);
    }
}
