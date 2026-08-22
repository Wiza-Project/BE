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
 *   <li>학생 1명당 1개의 취업 희망 프로필 원장을 관리 (student_id unique 제약)</li>
 *   <li>JobPosting과 동일하게 NCS 직무분류 및 근무지역은 {@link CommonCode}를 다대일(LAZY) 참조</li>
 *   <li>TODO: 잡매칭(Job Matching) 2-Way 추천 엔진 점수 산출을 위한 핵심 기준 데이터로 활용 예정</li>
 *   <li>엔티티 수정은 Dirty Checking 기반의 {@link #update} 비즈니스 메서드를 통해서만 수행</li>
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
    @JoinColumn(name = "ncs_code_id", nullable = true)
    private CommonCode ncsCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_region_code_id", nullable = true)
    private CommonCode regionCode;

    @Column(name = "preferred_employment_type", nullable = true, length = 30)
    private String preferredEmploymentType;

    @Column(name = "minimum_salary", nullable = true, precision = 14, scale = 2)
    private BigDecimal minimumSalary;

    /**
     * 학생 취업 희망조건 신규 생성을 위한 빌더 생성자
     *
     * <p><strong>[제약사항 및 처리 규칙]</strong></p>
     * <ul>
     *   <li>학생 계정({@code student})은 필수 매핑 대상이며 중복될 수 없습니다.</li>
     *   <li>직무({@code ncsCode}), 지역({@code regionCode}), 고용형태, 연봉은 선택 입력(NULL 허용) 항목입니다.</li>
     * </ul>
     *
     * @param student                 대상 학생 계정 엔티티 (NOT NULL)
     * @param ncsCode                 희망 NCS 직무 분류 공통코드 (nullable)
     * @param regionCode              희망 근무 지역 공통코드 (nullable)
     * @param preferredEmploymentType 희망 고용형태 (REGULAR, CONTRACT, INTERN 등, nullable)
     * @param minimumSalary           희망 최소 연봉 (nullable)
     */
    @Builder
    public JobPreference(AppUser student, CommonCode ncsCode, CommonCode regionCode,
                         String preferredEmploymentType, BigDecimal minimumSalary) {
        this.student = student;
        this.ncsCode = ncsCode;
        this.regionCode = regionCode;
        this.preferredEmploymentType = preferredEmploymentType;
        this.minimumSalary = minimumSalary;
    }

    /**
     * 학생 취업 희망조건 수정 (비즈니스 상태 변경 도메인 메서드)
     *
     * <p><strong>[상태 변경 규칙]</strong></p>
     * <ul>
     *   <li>최초 등록된 학생 식별자({@code student})는 고정되며 수정되지 않습니다.</li>
     *   <li>전달된 파라미터 값으로 기존 희망 조건 원장을 Dirty Checking 방식으로 일괄 갱신합니다.</li>
     * </ul>
     *
     * @param ncsCode                 변경할 희망 NCS 직무 분류 공통코드 (nullable)
     * @param regionCode              변경할 희망 근무 지역 공통코드 (nullable)
     * @param preferredEmploymentType 변경할 희망 고용형태 (nullable)
     * @param minimumSalary           변경할 희망 최소 연봉 (nullable)
     */
    public void update(CommonCode ncsCode, CommonCode regionCode,
                       String preferredEmploymentType, BigDecimal minimumSalary) {
        this.ncsCode = ncsCode;
        this.regionCode = regionCode;
        this.preferredEmploymentType = preferredEmploymentType;
        this.minimumSalary = minimumSalary;
    }

}
