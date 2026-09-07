package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 재학생 한정 백분위 재계산 백필(AssessmentPercentileBackfillRunner) 전용 — 지정 회차에서 재학생이
     * 아닌 응시자의 percentile을 NULL로 지운다. 재학생 한정 모수 밖의 값이라 의미가 없어 비운다
     * (FE는 null 백분위를 이미 안전 렌더 — AssessmentResultResponse 참고).
     *
     * <p>재학생 판정은 AssessmentTargetPolicy.ENROLLED_STUDENT와 같지만 QueryDSL BooleanExpression을
     * JPQL @Query에 넣을 수 없어 이 백필 한정으로만 '재학'·'STUDENT' 리터럴을 둔다 — 판정 기준의 정본은
     * 여전히 AssessmentTargetPolicy다.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE AssessmentScore s SET s.percentile = NULL
            WHERE s.attempt.assessmentRound.assessmentRoundId = :roundId
              AND (s.attempt.student.userType <> 'STUDENT'
                   OR s.attempt.student.academicStatus <> '재학'
                   OR s.attempt.student.academicStatus IS NULL)
            """)
    int nullifyNonEnrolledPercentiles(@Param("roundId") Integer roundId);
}
