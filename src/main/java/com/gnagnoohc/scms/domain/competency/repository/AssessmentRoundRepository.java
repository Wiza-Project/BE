package com.gnagnoohc.scms.domain.competency.repository;

import com.gnagnoohc.scms.domain.competency.entity.AssessmentRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AssessmentRoundRepository extends JpaRepository<AssessmentRound, Integer> {
    Optional<AssessmentRound> findByAcademicYearAndSemesterCodeAndAssessmentType(
            Integer academicYear, String semesterCode, String assessmentType);

    Optional<AssessmentRound> findByAcademicYearAndSemesterCodeAndAssessmentTypeAndAssessmentRoundIdNot(
            Integer academicYear, String semesterCode, String assessmentType, Integer assessmentRoundId);

    // 백분위 산출 배치(AssessmentPercentileBatchService) 대상 조회: 응시기간이 끝났지만 아직 완료 표시가 안 된 회차.
    List<AssessmentRound> findByEndsAtBeforeAndRoundStatusNot(Instant now, String roundStatus);
}
