package com.gnagnoohc.scms.domain.mileage.repository;

import com.gnagnoohc.scms.domain.mileage.entity.MileageBenefitPolicy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 선택 학기에 적용할 활성 인증·장학 정책을 조회한다. */
public interface MileageBenefitPolicyRepository extends JpaRepository<MileageBenefitPolicy, Integer>,
        JpaSpecificationExecutor<MileageBenefitPolicy> {

    /** 학생 마일리지 시뮬레이션에서 선택할 수 있는 활성 인증·장학 정책을 조회한다. */
    java.util.Optional<MileageBenefitPolicy> findByBenefitPolicyIdAndActiveTrue(
            Integer benefitPolicyId
    );

    /** 선택 학기에 적용되는 활성 마일리지 등급 정책을 최소 점수 오름차순으로 조회한다. */
    List<MileageBenefitPolicy> findByActiveTrueAndBenefitTypeAndSemesterCodeInOrderByMinimumPointsAsc(
            String benefitType,
            Collection<String> semesterCodes
    );

    /** 학생 장학금 상세·신청 대상 정책을 조회한다. */
    java.util.Optional<MileageBenefitPolicy> findByBenefitPolicyIdAndBenefitTypeAndActiveTrue(
            Integer benefitPolicyId,
            String benefitType
    );

    /** 해당 학기와 연간(ALL) 정책을 목표 점수 오름차순으로 조회한다. */
    List<MileageBenefitPolicy> findByActiveTrueAndSemesterCodeInOrderByMinimumPointsAsc(
            Collection<String> semesterCodes
    );

    /**
     * 정책 row에 비관적 락을 걸어 조회한다(MileagePolicyRepository.findByIdForUpdate와 동일 패턴).
     * update()의 조회→병합→저장 전체를 이 락 아래에서 수행해야, 두 교직원이 같은 정책을 동시에
     * 부분 수정할 때 나중 커밋이 먼저 커밋된 필드를 옛 값으로 덮어쓰는 lost update를 막을 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM MileageBenefitPolicy p WHERE p.benefitPolicyId = :benefitPolicyId")
    Optional<MileageBenefitPolicy> findByIdForUpdate(@Param("benefitPolicyId") Integer benefitPolicyId);
}
