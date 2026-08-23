package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssessmentRoundRepository extends JpaRepository<AssessmentRound, Integer> {
    Optional<AssessmentRound> findByAcademicYearAndSemesterCodeAndAssessmentType(
            Integer academicYear, String semesterCode, String assessmentType);

    Optional<AssessmentRound> findByAcademicYearAndSemesterCodeAndAssessmentTypeAndAssessmentRoundIdNot(
            Integer academicYear, String semesterCode, String assessmentType, Integer assessmentRoundId);
}
