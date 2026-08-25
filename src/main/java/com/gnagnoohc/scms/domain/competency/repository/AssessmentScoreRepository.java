package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssessmentScoreRepository extends JpaRepository<AssessmentScore, Integer> {

    // 백분위 산출 배치가 회차 하나의 전체 점수를 역량별로 묶어 계산해야 하므로 competency를 fetch join해 N+1을 피한다.
    @Query("""
            SELECT s FROM AssessmentScore s
            JOIN FETCH s.competency
            WHERE s.attempt.assessmentRound.assessmentRoundId = :roundId
            """)
    List<AssessmentScore> findByRoundIdFetchCompetency(@Param("roundId") Integer roundId);

    // 결과 조회(방사형 차트)는 축 순서(Competency.displayOrder)대로 내려줘야 하므로 정렬까지 쿼리에서 처리한다.
    @Query("""
            SELECT s FROM AssessmentScore s
            JOIN FETCH s.competency
            WHERE s.attempt.attemptId = :attemptId
            ORDER BY s.competency.displayOrder ASC
            """)
    List<AssessmentScore> findByAttemptIdFetchCompetencyOrderByDisplayOrder(@Param("attemptId") Integer attemptId);
}
