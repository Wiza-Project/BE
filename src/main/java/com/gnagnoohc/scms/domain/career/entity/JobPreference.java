package com.gnagnoohc.scms.domain.career.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 학생 취업 희망조건 엔티티 (JOB_PREFERENCE)
 *
 * <p><strong>[도메인 비즈니스 및 설계 기준]</strong></p>
 * <ul>
 *   <li>학생 1명당 1개의 취업 희망 프로필 원장을 관리한다 (student_id unique 제약).</li>
 *   <li>잡매칭(Job Matching) 2-Way 추천 엔진 점수 산출을 위한 핵심 기준 데이터로 활용된다.</li>
 *   <li>NCS 직무분류(NcsStandard) 및 근무지역(CommonCode)은 지연 로딩(LAZY)을 통해 최적화한다.</li>
 *   <li>엔티티 수정은 Dirty Checking 기반의 {@link #update} 비즈니스 메서드를 통해서만 수행한다.</li>
 * </ul>
 *
 * @author YUN
 */
@Entity
@Getter
@Table(name = "job_preference")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPreference extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_preference_id", nullable = false)
    private Integer jobPreferenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    private AppUser student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ncs_standard_id", nullable = true)
    private NcsStandard ncsStandard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_region_code_id", nullable = true)
    private CommonCode preferredRegionCode;

    @Column(name = "preferred_employment_type", nullable = true, length = 30)
    private String preferredEmploymentType;

    @Column(name = "minimum_salary", nullable = true, precision = 14, scale = 2)
    private BigDecimal minimumSalary;

    /**
     * 학생 희망조건 신규 생성용 빌더
     */
    @Builder
    public JobPreference(AppUser student, NcsStandard ncsStandard, CommonCode preferredRegionCode,
                         String preferredEmploymentType, BigDecimal minimumSalary) {
        this.student = student;
        this.ncsStandard = ncsStandard;
        this.preferredRegionCode = preferredRegionCode;
        this.preferredEmploymentType = preferredEmploymentType;
        this.minimumSalary = minimumSalary;
    }

    /**
     * 학생 취업 희망조건 수정 (비즈니스 상태 변경 메서드)
     *
     * @param ncsStandard             변경할 NCS 직무 표준 (nullable)
     * @param preferredRegionCode     변경할 희망 근무 지역 공통코드 (nullable)
     * @param preferredEmploymentType 변경할 희망 고용형태 (nullable)
     * @param minimumSalary           변경할 희망 최소 연봉 (nullable)
     */
    public void update(NcsStandard ncsStandard, CommonCode preferredRegionCode,
                       String preferredEmploymentType, BigDecimal minimumSalary) {
        this.ncsStandard = ncsStandard;
        this.preferredRegionCode = preferredRegionCode;
        this.preferredEmploymentType = preferredEmploymentType;
        this.minimumSalary = minimumSalary;
    }

}
