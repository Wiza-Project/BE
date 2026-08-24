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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

public interface MileagePolicyRepository extends JpaRepository<MileagePolicy, Integer>,
        JpaSpecificationExecutor<MileagePolicy> {

    /**
     * MileagePolicy 엔티티는 protected 기본 생성자만 있고 빌더/setter가 없어서
     * (ExtracurricularProgramRepository.insertProgram과 같은 이유) native INSERT로 우회한다.
     * duplicate_rule은 jsonb 컬럼이라 서비스에서 문자열로 직렬화한 값을 CAST로 넘긴다.
     */
    @Query(value = """
        INSERT INTO mileage_policy (
            activity_type_id, academic_year, semester_code, version_no,
            points, maximum_points, valid_from, valid_to, duplicate_rule,
            policy_status, created_by, created_at
        ) VALUES (
            :activityTypeId, :academicYear, :semesterCode, :versionNo,
            :points, :maximumPoints, :validFrom, :validTo, CAST(:duplicateRule AS jsonb),
            :policyStatus, :createdBy, :now
        )
        RETURNING mileage_policy_id
        """, nativeQuery = true)
    Integer insertPolicy(@Param("activityTypeId") Integer activityTypeId,
                          @Param("academicYear") Integer academicYear,
                          @Param("semesterCode") String semesterCode,
                          @Param("versionNo") Integer versionNo,
                          @Param("points") BigDecimal points,
                          @Param("maximumPoints") BigDecimal maximumPoints,
                          @Param("validFrom") LocalDate validFrom,
                          @Param("validTo") LocalDate validTo,
                          @Param("duplicateRule") String duplicateRule,
                          @Param("policyStatus") String policyStatus,
                          @Param("createdBy") Integer createdBy,
                          @Param("now") Instant now);

    /**
     * 활동유형+학년도+학기 조합 내 다음 버전 번호(MAX+1)를 계산한다. 관리자가 직접 입력하지 않고
     * 서버가 자동 채번해서 중복 버전 등록 실수를 막는다.
     */
    @Query("""
        SELECT COALESCE(MAX(p.versionNo), 0) + 1 FROM MileagePolicy p
        WHERE p.activityType.activityTypeId = :activityTypeId
          AND p.academicYear = :academicYear
          AND p.semesterCode = :semesterCode
        """)
    Integer findNextVersionNo(@Param("activityTypeId") Integer activityTypeId,
                               @Param("academicYear") Integer academicYear,
                               @Param("semesterCode") String semesterCode);

    /**
     * 정책 row에 비관적 락을 걸어 조회한다(ExtracurricularProgramRepository.findByIdForUpdate와 동일 패턴).
     * update()의 조회→null-병합→UPDATE 전체를 이 락 아래에서 수행해야, 두 관리자가 같은 정책을
     * 동시에 부분 수정할 때 나중 커밋이 먼저 커밋된 필드를 옛 값으로 덮어쓰는 lost update를 막을 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM MileagePolicy p WHERE p.mileagePolicyId = :mileagePolicyId")
    Optional<MileagePolicy> findByIdForUpdate(@Param("mileagePolicyId") Integer mileagePolicyId);

    /**
     * 식별 필드(activity_type_id/academic_year/semester_code/version_no)를 제외한 가변 필드만
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
