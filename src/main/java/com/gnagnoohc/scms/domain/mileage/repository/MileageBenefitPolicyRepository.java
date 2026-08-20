package com.gnagnoohc.scms.domain.mileage.repository;

import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface MileageBenefitPolicyRepository extends JpaRepository<MileageBenefitPolicy, Integer> {

    List<MileageBenefitPolicy> findByActiveTrueAndAcademicYearAndSemesterCodeInOrderByMinimumPointsAsc(
            Integer academicYear,
            Collection<String> semesterCodes
    );
}
