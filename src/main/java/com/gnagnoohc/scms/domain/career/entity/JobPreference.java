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
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
}
