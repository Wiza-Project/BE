package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssessmentAttemptRepository extends JpaRepository<AssessmentAttempt, Integer> {
    // 실제로 문항 응답을 시작한 attempt가 있는지 판정 (동의만 하고 아직 안 푼 attempt는 "시작 안 함"으로 봄).
    boolean existsByAssessmentRound_AssessmentRoundIdAndStartedAtIsNotNull(Integer assessmentRoundId);

    // 동의 API 멱등 처리용 — 이미 동의(=attempt 생성)한 학생이 다시 요청하면 새로 만들지 않고 기존 attempt를 그대로 돌려준다.
    Optional<AssessmentAttempt> findByAssessmentRound_AssessmentRoundIdAndStudent_UserId(
            Integer assessmentRoundId, Integer studentId);
}
