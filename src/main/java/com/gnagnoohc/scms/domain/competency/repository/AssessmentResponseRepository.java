package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentResponse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentResponseRepository extends JpaRepository<AssessmentResponse, Integer> {
    // 문항 편집 시 이미 응시(응답 저장)가 시작된 문항인지 판별 — 있으면 제자리 수정 대신 새 버전으로 대체한다.
    boolean existsByQuestion_QuestionId(Integer questionId);
}
