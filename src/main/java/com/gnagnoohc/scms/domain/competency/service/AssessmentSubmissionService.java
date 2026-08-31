package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.AssessmentSubmitResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRoundQuestion;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentResponseRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundQuestionRepository;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentRoundRepository;
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
    private final AssessmentRoundRepository assessmentRoundRepository;
    private final AssessmentScoreRepository assessmentScoreRepository;
    private final AssessmentScoreCalculator assessmentScoreCalculator;
    private final AssessmentResultReadyEventPublisher assessmentResultReadyEventPublisher;

    public AssessmentSubmitResponse submit(Integer attemptId, Integer studentId) {
        AssessmentAttempt attempt = assessmentAttemptAccessGuard.getOwnAttempt(attemptId, studentId);
        assessmentAttemptAccessGuard.assertNotSubmitted(attempt);
        assessmentAttemptAccessGuard.assertPeriodOpen(attempt);

        Integer roundId = attempt.getAssessmentRound().getAssessmentRoundId();
        List<AssessmentRoundQuestion> roundQuestions =
                assessmentRoundQuestionRepository.findByAssessmentRound_AssessmentRoundIdOrderByDisplayOrderAsc(roundId);

        // 문항이 하나도 매핑되지 않은 회차는 채점 대상이 없어 결과를 만들 수 없다.
        // assertAllAnswered는 미응답 문항이 없다는 이유로 공허하게 통과하므로 여기서 먼저 막는다.
        if (roundQuestions.isEmpty()) {
            throw new BusinessException(ErrorCode.ASSESSMENT_ROUND_NO_QUESTIONS);
        }

        Map<Integer, BigDecimal> selectedValuesByQuestionId = assessmentResponseRepository.findByAttempt_AttemptId(attemptId)
                .stream()
                .collect(Collectors.toMap(r -> r.getQuestion().getQuestionId(), AssessmentResponse::getSelectedValue));

        assertAllAnswered(roundQuestions, selectedValuesByQuestionId);

        List<AssessmentScoreCalculator.CompetencyScore> scores =
                assessmentScoreCalculator.calculate(roundQuestions, selectedValuesByQuestionId);

        // AssessmentPercentileBatchService(백분위 완료 배치)가 같은 회차 행에 거는 PESSIMISTIC_WRITE
        // 잠금을 여기서도 걸어 두 트랜잭션을 직렬화한다. 이 시점 이전(문항·응답 조회, 점수 계산)은 회차의
        // 생명주기와 무관한 순수 조회/계산이라 잠금 없이 진행해도 안전하다 — 잠금은 실제 영속 상태 변경
        // (submit/저장/markScored) 구간만 감싸 다른 제출자와의 불필요한 대기를 최소화한다.
        AssessmentRound lockedRound = assessmentRoundRepository.findByIdForUpdate(roundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_ROUND_NOT_FOUND));

        // 배치는 ends_at + 유예시간이 지난 뒤에만 이 잠금을 잡으므로, 이 잠금을 기다렸다가 재개된 것이라면
        // 실제 시계로도 이미 ends_at을 넘겨 있다 — 아래 assertPeriodOpen 재검증이 그 경우를 정확히 걸러낸다.
        // roundStatus 체크는 그 위에 얹는 방어적 이중 확인이다(둘 다 같은 DIAGNOSIS_PERIOD_CLOSED로 안내).
        if (lockedRound.isPercentileCalculationCompleted()) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_PERIOD_CLOSED);
        }
        assessmentAttemptAccessGuard.assertPeriodOpen(attempt);

        // submit()과 markScored()를 분리 호출하는 이유: 트랜잭션이 실패하면 어차피 둘 다 롤백되지만,
        // "제출 자체는 확정됐다"와 "환산점수 산출까지 끝났다"는 서로 다른 사실이라 상태를 나눠서 반영한다
        // (markScored는 saveAll이 성공한 뒤에만 호출 — 아래 catch 참고).
        attempt.submit();

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

        // 이력서 연동용 결과 준비 이벤트를 제출 트랜잭션 안에서 발행한다. 취창업은 AFTER_COMMIT로 구독하므로
        // 취창업 리스너 실패가 이 제출을 롤백하지 않는다. 방금 계산한 점수 리스트를 그대로 넘겨 재조회를 피한다.
        assessmentResultReadyEventPublisher.publishFromSubmit(attempt, scores);

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
