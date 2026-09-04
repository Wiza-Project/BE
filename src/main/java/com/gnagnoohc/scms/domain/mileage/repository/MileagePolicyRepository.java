package com.gnagnoohc.scms.domain.mileage.repository;

import com.gnagnoohc.scms.domain.mileage.entity.MileagePolicy;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MileagePolicyRepository extends JpaRepository<MileagePolicy, Integer>,
        JpaSpecificationExecutor<MileagePolicy>, MileagePolicyRepositoryCustom {

    /** 활동유형+학기 조합 내 다음 버전 번호(MAX+1)를 계산한다. */
    @Query("""
        SELECT COALESCE(MAX(p.versionNo), 0) + 1 FROM MileagePolicy p
        WHERE p.activityType.activityTypeId = :activityTypeId
          AND p.semesterCode = :semesterCode
        """)
    Integer findNextVersionNo(@Param("activityTypeId") Integer activityTypeId,
                               @Param("semesterCode") String semesterCode);

    /** 프로그램 유형에 연결된 비교과 전용 정책을 최신 버전부터 조회한다. */
    @Query("""
            select p
            from MileagePolicy p
            join fetch p.activityType activityType
            join fetch activityType.programTypeCode programTypeCode
            where programTypeCode.codeGroup = 'PROGRAM_TYPE'
              and programTypeCode.code = :programTypeCode
              and activityType.categoryCode = :categoryCode
              and activityType.earningRoute = :earningRoute
              and p.policyStatus = 'ACTIVE'
              and activityType.active = true
              and p.validFrom <= :asOfDate
              and (p.validTo is null or p.validTo >= :asOfDate)
            order by p.versionNo desc
            """)
    List<MileagePolicy> findActiveExtracurricularPoliciesByProgramTypeOn(
            @Param("programTypeCode") String programTypeCode,
            @Param("categoryCode") String categoryCode,
            @Param("earningRoute") String earningRoute,
            @Param("asOfDate") LocalDate asOfDate
    );

    /**
     * 외부활동 신청일에 적용되는 활성 정책을 조회한다. 신청일이 속한 학기(semesterCode) 전용 정책을
     * 학기 무관(ALL) 정책보다 우선하고, 그 안에서는 최신 버전을 우선한다.
     */
    @Query("""
            select p
            from MileagePolicy p
            join fetch p.activityType activityType
            where activityType.activityTypeId = :activityTypeId
              and activityType.active = true
              and p.policyStatus = 'ACTIVE'
              and p.validFrom <= :activityDate
              and (p.validTo is null or p.validTo >= :activityDate)
              and (p.semesterCode = :semesterCode or p.semesterCode = 'ALL')
            order by case when p.semesterCode = :semesterCode then 0 else 1 end, p.versionNo desc
            """)
    List<MileagePolicy> findActivePoliciesByActivityTypeOn(
            @Param("activityTypeId") Integer activityTypeId,
            @Param("activityDate") LocalDate activityDate,
            @Param("semesterCode") String semesterCode
    );

    /**
     * 학생 외부활동 등록 화면에 표시할 현재 적용 가능한 외부활동 정책을 조회한다. 기준일이 속한
     * 학기(semesterCode) 전용 정책을 학기 무관(ALL) 정책보다 우선하고, 그 안에서는 최신 버전을 우선한다.
     */
    @Query("""
            select p
            from MileagePolicy p
            join fetch p.activityType activityType
            where activityType.programTypeCode is null
              and activityType.competency is not null
              and upper(activityType.earningRoute) not in :excludedEarningRoutes
              and activityType.active = true
              and p.policyStatus = 'ACTIVE'
              and p.points > 0
              and p.validFrom <= :asOfDate
              and (p.validTo is null or p.validTo >= :asOfDate)
              and (p.semesterCode = :semesterCode or p.semesterCode = 'ALL')
            order by activityType.activityName asc,
                     case when p.semesterCode = :semesterCode then 0 else 1 end,
                     p.versionNo desc
            """)
    List<MileagePolicy> findActiveExternalPoliciesOn(
            @Param("asOfDate") LocalDate asOfDate,
            @Param("excludedEarningRoutes") Collection<String> excludedEarningRoutes,
            @Param("semesterCode") String semesterCode
    );

    /**
     * 정책 row에 비관적 락을 걸어 조회한다(ExtracurricularProgramRepository.findByIdForUpdate와 동일 패턴).
     * update()의 조회→null-병합→UPDATE 전체를 이 락 아래에서 수행해야, 두 교직원이 같은 정책을
     * 동시에 부분 수정할 때 나중 커밋이 먼저 커밋된 필드를 옛 값으로 덮어쓰는 lost update를 막을 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM MileagePolicy p WHERE p.mileagePolicyId = :mileagePolicyId")
    Optional<MileagePolicy> findByIdForUpdate(@Param("mileagePolicyId") Integer mileagePolicyId);

    /**
     * 식별 필드(activity_type_id/semester_code/version_no)를 제외한 가변 필드만
     * 부분 수정한다. 테이블에 updated_at/updated_by 컬럼이 없어 그 값은 갱신하지 않는다.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE mileage_policy
        SET points = :points,
            maximum_points = :maximumPoints,
            valid_from = :validFrom,
            valid_to = :validTo,
            duplicate_rule = CAST(:duplicateRule AS jsonb),
            policy_status = :policyStatus
        WHERE mileage_policy_id = :mileagePolicyId
        """, nativeQuery = true)
    int updatePolicy(@Param("mileagePolicyId") Integer mileagePolicyId,
                      @Param("points") BigDecimal points,
                      @Param("maximumPoints") BigDecimal maximumPoints,
                      @Param("validFrom") LocalDate validFrom,
                      @Param("validTo") LocalDate validTo,
                      @Param("duplicateRule") String duplicateRule,
                      @Param("policyStatus") String policyStatus);

    // 목록 조회 시 활동유형을 함께 페치해서 N+1 조회를 막는다.
    @EntityGraph(attributePaths = "activityType")
    Page<MileagePolicy> findAll(Specification<MileagePolicy> spec, Pageable pageable);

}
