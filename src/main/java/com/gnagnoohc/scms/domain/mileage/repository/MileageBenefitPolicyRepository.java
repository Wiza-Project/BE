package com.gnagnoohc.scms.domain.mileage.repository;

import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** 선택 학기에 적용할 활성 인증·장학 정책을 조회한다. */
public interface MileageBenefitPolicyRepository extends JpaRepository<MileageBenefitPolicy, Integer> {

    /** 해당 학기와 연간(ALL) 정책을 목표 점수 오름차순으로 조회한다. */
    List<MileageBenefitPolicy> findByActiveTrueAndAcademicYearAndSemesterCodeInOrderByMinimumPointsAsc(
            Integer academicYear,
            Collection<String> semesterCodes
    );
}
