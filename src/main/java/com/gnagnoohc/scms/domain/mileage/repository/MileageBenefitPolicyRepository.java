package com.gnagnoohc.scms.domain.mileage.repository;

import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** 선택 학기에 적용할 활성 인증·장학 정책을 조회한다. */
public interface MileageBenefitPolicyRepository extends JpaRepository<MileageBenefitPolicy, Integer> {

    /** 학생에게 공개할 활성 장학금 정책을 학기와 연간 정책으로 나누어 조회한다. */
    List<MileageBenefitPolicy> findByActiveTrueAndBenefitTypeAndAcademicYearAndSemesterCodeInOrderByMinimumPointsAsc(
            String benefitType,
            Integer academicYear,
            Collection<String> semesterCodes
    );

    /** 학생 장학금 상세·신청 대상 정책을 조회한다. */
    java.util.Optional<MileageBenefitPolicy> findByBenefitPolicyIdAndBenefitTypeAndActiveTrue(
            Integer benefitPolicyId,
            String benefitType
    );

    /** 해당 학기와 연간(ALL) 정책을 목표 점수 오름차순으로 조회한다. */
    List<MileageBenefitPolicy> findByActiveTrueAndAcademicYearAndSemesterCodeInOrderByMinimumPointsAsc(
            Integer academicYear,
            Collection<String> semesterCodes
    );
}
