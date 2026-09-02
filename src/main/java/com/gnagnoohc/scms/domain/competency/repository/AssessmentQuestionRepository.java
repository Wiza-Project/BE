package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, Integer> {
    long countByCompetency_CompetencyIdAndActiveTrue(Integer competencyId);

    // 편집 폼에서 역량별 현재 유효(최신) 문항 목록을 보여줄 때 사용
    List<AssessmentQuestion> findByCompetency_CompetencyIdAndActiveTrueOrderByQuestionIdAsc(Integer competencyId);

    // 회차 개설 시 편성 대상 — 현재 활성인 최상위 역량 문항 전량을 역량 축순서 → 문항ID 순으로.
    @Query("select q from AssessmentQuestion q join fetch q.competency c "
            + "where q.active = true and c.parentCompetency is null "
            + "order by c.displayOrder asc, q.questionId asc")
    List<AssessmentQuestion> findAllActiveForRoundComposition();
}
