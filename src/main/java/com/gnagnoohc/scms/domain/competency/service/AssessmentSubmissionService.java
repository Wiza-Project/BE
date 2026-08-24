package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentSubmitResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRoundQuestion;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentResponseRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentScoreRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class AssessmentSubmissionService {

    private final AssessmentAttemptAccessGuard assessmentAttemptAccessGuard;
    private final AssessmentResponseRepository assessmentResponseRepository;
    private final AssessmentRoundQuestionRepository assessmentRoundQuestionRepository;
    private final AssessmentScoreRepository assessmentScoreRepository;
    private final AssessmentScoreCalculator assessmentScoreCalculator;

    public AssessmentSubmitResponse submit(Integer attemptId, Integer studentId) {
        AssessmentAttempt attempt = assessmentAttemptAccessGuard.getOwnAttempt(attemptId, studentId);
        assessmentAttemptAccessGuard.assertNotSubmitted(attempt);
        assessmentAttemptAccessGuard.assertPeriodOpen(attempt);

        Integer roundId = attempt.getAssessmentRound().getAssessmentRoundId();
        List<AssessmentRoundQuestion> roundQuestions =
                assessmentRoundQuestionRepository.findByAssessmentRound_AssessmentRoundIdOrderByDisplayOrderAsc(roundId);

        Map<Integer, BigDecimal> selectedValuesByQuestionId = assessmentResponseRepository.findByAttempt_AttemptId(attemptId)
                .stream()
                .collect(Collectors.toMap(r -> r.getQuestion().getQuestionId(), AssessmentResponse::getSelectedValue));

        assertAllAnswered(roundQuestions, selectedValuesByQuestionId);

        // submit()과 markScored()를 분리 호출하는 이유: 트랜잭션이 실패하면 어차피 둘 다 롤백되지만,
        // "제출 자체는 확정됐다"와 "환산점수 산출까지 끝났다"는 서로 다른 사실이라 상태를 나눠서 반영한다
        // (markScored는 saveAll이 성공한 뒤에만 호출 — 아래 catch 참고).
        attempt.submit();

        List<AssessmentScoreCalculator.CompetencyScore> scores =
                assessmentScoreCalculator.calculate(roundQuestions, selectedValuesByQuestionId);

        List<AssessmentScore> scoreEntities = scores.stream()
                .map(s -> AssessmentScore.create(attempt, s.competency(), s.rawScore(), s.convertedScore()))
                .toList();

        try {
            assessmentScoreRepository.saveAll(scoreEntities);
        } catch (DataIntegrityViolationException e) {
            // uq_assessment_score_attempt_competency 위반 = 동시 제출 요청 중 하나가 먼저 점수 저장까지 커밋 완료함
            // (AssessmentResponseService.saveResponse의 RESPONSE_SAVE_CONFLICT 처리와 동일한 이유).
            // 이 시점엔 상대 트랜잭션의 submittedAt도 이미 커밋되어 있으므로 "이미 제출됨"으로 안내한다.
            throw new BusinessException(ErrorCode.DIAGNOSIS_ALREADY_SUBMITTED);
        }

        attempt.markScored();

        List<AssessmentSubmitResponse.CompetencyScore> scoreDtos = scores.stream()
                .map(s -> new AssessmentSubmitResponse.CompetencyScore(
                        s.competency().getCompetencyId(),
                        s.competency().getCompetencyName(),
                        s.competency().getDisplayOrder(),
                        s.rawScore(),
                        s.convertedScore()))
                .toList();

        return new AssessmentSubmitResponse(
                attempt.getAttemptId(), attempt.getAttemptStatus(), attempt.getSubmittedAt(), scoreDtos);
    }

    // 미응답 문항이 있으면 제출 불가 — FE가 해당 문항으로 이동할 수 있도록 미응답 questionId 목록을 data로 함께 반환한다.
    private void assertAllAnswered(List<AssessmentRoundQuestion> roundQuestions,
                                    Map<Integer, BigDecimal> selectedValuesByQuestionId) {
        List<Integer> missingQuestionIds = new ArrayList<>();
        for (AssessmentRoundQuestion rq : roundQuestions) {
            Integer questionId = rq.getQuestion().getQuestionId();
            if (!selectedValuesByQuestionId.containsKey(questionId)) {
                missingQuestionIds.add(questionId);
            }
        }
        if (!missingQuestionIds.isEmpty()) {
            throw BusinessException.withData(ErrorCode.INCOMPLETE_ANSWER, missingQuestionIds);
        }
    }
}
