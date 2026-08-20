package com.gnagnoohc.scms.domain.mileage.repository;

import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MileageBenefitApplicationRepository extends JpaRepository<MileageBenefitApplication, Integer> {

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

    interface ApplicationStatusProjection {
        Integer getBenefitPolicyId();

        String getApplicationStatus();
    }
}
