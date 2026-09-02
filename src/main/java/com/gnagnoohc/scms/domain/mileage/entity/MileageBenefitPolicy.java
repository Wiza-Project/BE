package com.gnagnoohc.scms.domain.mileage.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/** 학기별 인증·장학금 혜택의 최소 점수와 신청 기간을 정의한다. */
@Entity @Getter @Table(name = "mileage_benefit_policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MileageBenefitPolicy extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "benefit_policy_id", nullable = false) private Integer benefitPolicyId;
    @Column(name = "benefit_type", nullable = false, length = 20) private String benefitType;
    @Column(name = "academic_year", nullable = false) private Integer academicYear;
    @Column(name = "semester_code", nullable = false, length = 20) private String semesterCode = "ALL";
    @Column(name = "benefit_name", nullable = false, length = 150) private String benefitName;
    @Column(name = "minimum_points", nullable = false, precision = 10, scale = 2) private BigDecimal minimumPoints;
    @Column(name = "benefit_amount", precision = 14, scale = 2) private BigDecimal benefitAmount;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "criteria_data", columnDefinition = "jsonb") private JsonNode criteriaData;
    @Column(name = "application_starts_at") private Instant applicationStartsAt;
    @Column(name = "application_ends_at") private Instant applicationEndsAt;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "created_by", nullable = false) private Integer createdBy;

    public static MileageBenefitPolicy create(
            String benefitType,
            Integer academicYear,
            String semesterCode,
            String benefitName,
            BigDecimal minimumPoints,
            BigDecimal benefitAmount,
            JsonNode criteriaData,
            Instant applicationStartsAt,
            Instant applicationEndsAt,
            Integer createdBy
    ) {
        MileageBenefitPolicy policy = new MileageBenefitPolicy();
        policy.benefitType = benefitType;
        policy.academicYear = academicYear;
        policy.semesterCode = semesterCode;
        policy.benefitName = benefitName;
        policy.minimumPoints = minimumPoints;
        policy.benefitAmount = benefitAmount;
        policy.criteriaData = criteriaData;
        policy.applicationStartsAt = applicationStartsAt;
        policy.applicationEndsAt = applicationEndsAt;
        policy.createdBy = createdBy;
        return policy;
    }

    /** 요청 필드가 null이면 기존 값을 유지하는 부분 수정. active만 boolean이라 별도 파라미터로 받는다. */
    public void update(
            String benefitName,
            BigDecimal minimumPoints,
            BigDecimal benefitAmount,
            JsonNode criteriaData,
            Instant applicationStartsAt,
            Instant applicationEndsAt,
            Boolean active
    ) {
        if (benefitName != null) {
            this.benefitName = benefitName;
        }
        if (minimumPoints != null) {
            this.minimumPoints = minimumPoints;
        }
        if (benefitAmount != null) {
            this.benefitAmount = benefitAmount;
        }
        if (criteriaData != null) {
            this.criteriaData = criteriaData;
        }
        if (applicationStartsAt != null) {
            this.applicationStartsAt = applicationStartsAt;
        }
        if (applicationEndsAt != null) {
            this.applicationEndsAt = applicationEndsAt;
        }
        if (active != null) {
            this.active = active;
        }
    }
}
