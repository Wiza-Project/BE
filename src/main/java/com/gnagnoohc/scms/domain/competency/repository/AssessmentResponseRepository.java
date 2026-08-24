package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AssessmentResponseRepository extends JpaRepository<AssessmentResponse, Integer> {
    // 문항 편집 시 이미 응시(응답 저장)가 시작된 문항인지 판별 — 있으면 제자리 수정 대신 새 버전으로 대체한다.
    boolean existsByQuestion_QuestionId(Integer questionId);

    // 응답 저장(중도저장) upsert 조회용 — 있으면 update, 없으면 insert.
    Optional<AssessmentResponse> findByAttempt_AttemptIdAndQuestion_QuestionId(Integer attemptId, Integer questionId);

    // 이어하기 조회용 — 문항 텍스트 표시를 위해 question을 fetch join으로 함께 로딩(N+1 방지).
    @Query("select r from AssessmentResponse r join fetch r.question where r.attempt.attemptId = :attemptId")
    List<AssessmentResponse> findByAttempt_AttemptId(Integer attemptId);

    // 진행률(answeredCount) 계산용 — 전체 응답 로딩 없이 개수만 필요할 때 사용.
    long countByAttempt_AttemptId(Integer attemptId);
}
