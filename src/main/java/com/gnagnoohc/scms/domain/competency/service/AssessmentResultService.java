package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.dto.response.AssessmentResultResponse;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentScoreRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssessmentResultService {

    private final AssessmentAttemptAccessGuard assessmentAttemptAccessGuard;
    private final AssessmentScoreRepository assessmentScoreRepository;

    public AssessmentResultResponse getResult(Integer attemptId, Integer studentId) {
        AssessmentAttempt attempt = assessmentAttemptAccessGuard.getOwnAttempt(attemptId, studentId);

        List<AssessmentScore> scores =
                assessmentScoreRepository.findByAttemptIdFetchCompetencyOrderByDisplayOrder(attemptId);
        // 제출 트랜잭션에서 환산점수까지 같이 저장되므로(AssessmentSubmissionService), 점수가 하나도
        // 없다는 건 아직 제출 전(NOT_STARTED/IN_PROGRESS)이라는 뜻이다 — attemptStatus 대신 이 사실로 판정한다.
        if (scores.isEmpty()) {
            throw new BusinessException(ErrorCode.RESULT_NOT_AVAILABLE);
        }

        boolean percentileAvailable = attempt.getAssessmentRound().isPercentileCalculationCompleted();

        return AssessmentResultResponse.from(attempt, scores, percentileAvailable);
    }
}
