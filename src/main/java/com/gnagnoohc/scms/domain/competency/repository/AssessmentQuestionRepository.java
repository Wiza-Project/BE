package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, Integer> {
    long countByCompetency_CompetencyIdAndActiveTrue(Integer competencyId);

    // 편집 폼에서 역량별 현재 유효(최신) 문항 목록을 보여줄 때 사용
    List<AssessmentQuestion> findByCompetency_CompetencyIdAndActiveTrueOrderByQuestionIdAsc(Integer competencyId);
}
