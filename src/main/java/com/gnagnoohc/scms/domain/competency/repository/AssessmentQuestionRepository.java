package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, Integer> {
    long countByCompetency_CompetencyIdAndActiveTrue(Integer competencyId);
}
