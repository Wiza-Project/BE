package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentRoundQuestion;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRoundQuestionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AssessmentRoundQuestionRepository extends JpaRepository<AssessmentRoundQuestion, AssessmentRoundQuestionId> {
    // 이어하기 조회 시 회차에 속한 전체 문항을 표시 순서대로 반환 — 문항/역량을 fetch join으로 함께 로딩(N+1 방지).
    @Query("select rq from AssessmentRoundQuestion rq " +
            "join fetch rq.question q join fetch q.competency " +
            "where rq.assessmentRound.assessmentRoundId = :assessmentRoundId " +
            "order by rq.displayOrder asc")
    List<AssessmentRoundQuestion> findByAssessmentRound_AssessmentRoundIdOrderByDisplayOrderAsc(Integer assessmentRoundId);

    // 응답 저장 시 해당 문항이 실제로 이 회차에 속하는지 검증용.
    boolean existsByAssessmentRound_AssessmentRoundIdAndQuestion_QuestionId(Integer assessmentRoundId, Integer questionId);

    // 진행률(totalCount) 계산용 — fetch join 없이 개수만 필요할 때 사용.
    long countByAssessmentRound_AssessmentRoundId(Integer assessmentRoundId);

    // 학생 응시 가능 회차 목록 — 여러 회차의 문항 수를 한 번에 집계한다(회차별 count N+1 방지).
    // row[0] = assessmentRoundId(Integer), row[1] = 문항 수(Long). 문항이 0개인 회차는 결과에 나오지 않는다.
    @Query("select rq.assessmentRound.assessmentRoundId, count(rq) from AssessmentRoundQuestion rq "
            + "where rq.assessmentRound.assessmentRoundId in :roundIds "
            + "group by rq.assessmentRound.assessmentRoundId")
    List<Object[]> countGroupedByAssessmentRoundIds(@Param("roundIds") Collection<Integer> roundIds);
}
