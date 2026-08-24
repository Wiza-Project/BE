package com.gnagnoohc.scms.domain.competency.service;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.repository.AssessmentAttemptRepository;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

// AssessmentResponseService(응답 저장/이어하기)와 AssessmentSubmissionService(제출)가 공통으로 쓰는
// attempt 접근 권한·상태 검증 규칙. 두 서비스에 각각 복붙돼 있던 걸 여기로 모았다 — 정책이 바뀌면
// 여기 한 곳만 고치면 두 엔드포인트에 동시에 반영된다.
@Component
@RequiredArgsConstructor
public class AssessmentAttemptAccessGuard {

    private final AssessmentAttemptRepository assessmentAttemptRepository;

    // 다른 학생의 attempt에 접근하는 경우와 존재하지 않는 경우를 구분하지 않는다
    // (attempt 존재 여부·소유권을 응답으로 노출하지 않기 위함).
    public AssessmentAttempt getOwnAttempt(Integer attemptId, Integer studentId) {
        AssessmentAttempt attempt = assessmentAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSESSMENT_ATTEMPT_NOT_FOUND));
        if (!attempt.getStudent().getUserId().equals(studentId)) {
            throw new BusinessException(ErrorCode.ASSESSMENT_ATTEMPT_NOT_FOUND);
        }
        return attempt;
    }

    public void assertNotSubmitted(AssessmentAttempt attempt) {
        if (attempt.getSubmittedAt() != null) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_ALREADY_SUBMITTED);
        }
    }

    // 응시기간 이전(아직 시작 전)뿐 아니라 이후(마감 후 뒤늦은 제출/저장)도 함께 막는다 —
    // 편도 체크가 아니라 startsAt~endsAt 양끝을 다 검증해야 하는 규칙이라는 점이 이름만 봐선 안 드러난다.
    public void assertPeriodOpen(AssessmentAttempt attempt) {
        Instant now = Instant.now();
        var round = attempt.getAssessmentRound();
        if (now.isBefore(round.getStartsAt()) || now.isAfter(round.getEndsAt())) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_PERIOD_CLOSED);
        }
    }
}
