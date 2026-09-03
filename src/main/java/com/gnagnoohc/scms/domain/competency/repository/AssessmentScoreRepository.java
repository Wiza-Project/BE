package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssessmentScoreRepository extends JpaRepository<AssessmentScore, Integer> {

    // 결과 조회(방사형 차트)는 축 순서(Competency.displayOrder)대로 내려줘야 하므로 정렬까지 쿼리에서 처리한다.
    @Query("""
            SELECT s FROM AssessmentScore s
            JOIN FETCH s.competency
            WHERE s.attempt.attemptId = :attemptId
            ORDER BY s.competency.displayOrder ASC
            """)
    List<AssessmentScore> findByAttemptIdFetchCompetencyOrderByDisplayOrder(@Param("attemptId") Integer attemptId);
}
