package com.gnagnoohc.scms.domain.competency.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResponseProgress;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResponseSaveResponse;
import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResumeResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentQuestion;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRoundQuestion;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentResponseRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundQuestionRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AssessmentResponseService {

    private final AssessmentAttemptAccessGuard assessmentAttemptAccessGuard;
    private final AssessmentResponseRepository assessmentResponseRepository;
    private final AssessmentRoundQuestionRepository assessmentRoundQuestionRepository;
    private final AssessmentQuestionRepository assessmentQuestionRepository;

    // 기간이 지나도 이어하기 조회(읽기)는 허용한다 — 저장(쓰기)만 assertPeriodOpen으로 막는다.
    // 기간 안에 다 못 푼 학생이 어디까지 응답했는지는 볼 수 있어야 하기 때문(제출은 여전히 불가).
    @Transactional(readOnly = true)
    public AssessmentResumeResponse resume(Integer attemptId, Integer studentId) {
        AssessmentAttempt attempt = assessmentAttemptAccessGuard.getOwnAttempt(attemptId, studentId);
        assessmentAttemptAccessGuard.assertNotSubmitted(attempt);

        Integer roundId = attempt.getAssessmentRound().getAssessmentRoundId();
        List<AssessmentRoundQuestion> roundQuestions =
                assessmentRoundQuestionRepository.findByAssessmentRound_AssessmentRoundIdOrderByDisplayOrderAsc(roundId);

        Map<Integer, BigDecimal> selectedValuesByQuestionId = assessmentResponseRepository.findByAttempt_AttemptId(attemptId)
                .stream()
                .collect(Collectors.toMap(r -> r.getQuestion().getQuestionId(), AssessmentResponse::getSelectedValue));

        return AssessmentResumeResponse.from(attempt, roundQuestions, selectedValuesByQuestionId);
    }

    public AssessmentResponseSaveResponse saveResponse(Integer attemptId, Integer questionId,
                                                          BigDecimal selectedValue, Integer studentId) {
        AssessmentAttempt attempt = assessmentAttemptAccessGuard.getOwnAttempt(attemptId, studentId);
        assessmentAttemptAccessGuard.assertNotSubmitted(attempt);
        assessmentAttemptAccessGuard.assertPeriodOpen(attempt);
        assessmentAttemptAccessGuard.assertStillEnrolled(attempt);

        Integer roundId = attempt.getAssessmentRound().getAssessmentRoundId();
        if (!assessmentRoundQuestionRepository.existsByAssessmentRound_AssessmentRoundIdAndQuestion_QuestionId(roundId, questionId)) {
            throw new BusinessException(ErrorCode.QUESTION_NOT_IN_ROUND);
        }
        AssessmentQuestion question = assessmentQuestionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_QUESTION_NOT_FOUND));
        assertValidResponseValue(question, selectedValue);

        AssessmentResponse response;
        try {
            response = assessmentResponseRepository
                    .findByAttempt_AttemptIdAndQuestion_QuestionId(attemptId, questionId)
                    .map(existing -> {
                        existing.updateSelectedValue(selectedValue);
                        return existing;
                    })
                    .orElseGet(() -> assessmentResponseRepository.save(
                            AssessmentResponse.create(attempt, question, selectedValue, studentId)));
        } catch (DataIntegrityViolationException e) {
            // uq_assessment_response_attempt_question 위반 = 동시 요청이 먼저 이 문항의 첫 응답을 저장 완료함
            // (더블클릭/중복 탭/자동저장 재시도). 인메모리에서 재조회 후 update로 복구를 시도하지 않는 이유:
            // Postgres는 제약 위반으로 statement가 실패하면 트랜잭션 전체를 aborted 상태로 만들어서,
            // 같은 트랜잭션 안에서 재조회 쿼리를 또 날려도 그대로 실패한다(ProgramApplicationService.apply와 동일 판단).
            // 대신 깨끗한 CONFLICT로 던져서 500/에러로그 오염을 막는다 — PUT은 멱등하므로 클라이언트가
            // 같은 요청을 재시도하면 이번엔 findBy...에서 방금 커밋된 row를 찾아 정상적으로 update된다.
            throw new BusinessException(ErrorCode.RESPONSE_SAVE_CONFLICT);
        }

        attempt.start();
        attempt.touchSavedAt();

        long totalCount = assessmentRoundQuestionRepository.countByAssessmentRound_AssessmentRoundId(roundId);
        long answeredCount = assessmentResponseRepository.countByAttempt_AttemptId(attemptId);

        return new AssessmentResponseSaveResponse(
                questionId, response.getSelectedValue(), response.getSavedAt(),
                new AssessmentResponseProgress((int) answeredCount, (int) totalCount));
    }

    private void assertValidResponseValue(AssessmentQuestion question, BigDecimal selectedValue) {
        JsonNode options = question.getResponseOptions();
        boolean valid = false;
        for (JsonNode option : options) {
            // compareTo로 비교(equals 아님) — BigDecimal.equals는 scale까지 비교해서 "5"와 "5.00"을
            // 다른 값으로 취급한다. selectedValue는 DB precision(10,2)이라 클라이언트가 5를 보내면 5.00으로
            // 역직렬화될 수 있어, 값만 같으면 scale이 달라도 유효 응답으로 인정해야 한다.
            if (new BigDecimal(option.get("value").asText()).compareTo(selectedValue) == 0) {
                valid = true;
                break;
            }
        }
        if (!valid) {
            throw new BusinessException(ErrorCode.INVALID_RESPONSE_VALUE);
        }
    }
}
