package com.gnagnoohc.scms.domain.mileage.repository;

import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 학생의 인증·장학 신청 이력을 정책별로 조회한다. */
public interface MileageBenefitApplicationRepository extends JpaRepository<MileageBenefitApplication, Integer> {

    /** 특정 장학금에 대한 학생 본인의 기존 신청 여부를 확인한다. */
    Optional<MileageBenefitApplication> findByBenefitPolicy_BenefitPolicyIdAndStudent_UserId(
            Integer benefitPolicyId,
            Integer studentId
    );

    /** 학생 본인의 장학금 신청 이력을 최신 신청순으로 조회한다. */
    Page<MileageBenefitApplication> findAllByStudent_UserIdAndBenefitPolicy_BenefitTypeOrderByAppliedAtDesc(
            Integer studentId,
            String benefitType,
            Pageable pageable
    );

    /** 대시보드 정책 목록에 필요한 신청 상태만 일괄 조회한다. */
    @Query("""
            select a.benefitPolicy.benefitPolicyId as benefitPolicyId,
                   a.applicationStatus as applicationStatus
            from MileageBenefitApplication a
            where a.student.userId = :studentId
              and a.benefitPolicy.benefitPolicyId in :benefitPolicyIds
            """)
    List<ApplicationStatusProjection> findApplicationStatuses(
            @Param("studentId") Integer studentId,
            @Param("benefitPolicyIds") Collection<Integer> benefitPolicyIds
    );

    /** 정책별 신청 상태만 전달하는 조회 전용 결과다. */
    interface ApplicationStatusProjection {
        Integer getBenefitPolicyId();

        String getApplicationStatus();
    }
}
