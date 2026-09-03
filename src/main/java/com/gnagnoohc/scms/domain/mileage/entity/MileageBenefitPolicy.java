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
    @Column(name = "semester_code", nullable = false, length = 20) private String semesterCode = "ALL";
    @Column(name = "benefit_name", nullable = false, length = 150) private String benefitName;
    @Column(name = "minimum_points", nullable = false, precision = 10, scale = 2) private BigDecimal minimumPoints;
    @Column(name = "benefit_amount", precision = 14, scale = 2) private BigDecimal benefitAmount;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "criteria_data", columnDefinition = "jsonb") private JsonNode criteriaData;
    @Column(name = "application_starts_at") private Instant applicationStartsAt;
    @Column(name = "application_ends_at") private Instant applicationEndsAt;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "created_by", nullable = false) private Integer createdBy;
    /** 배타적 단계(tier) 그룹 식별자. 같은 값을 공유하는 정책끼리는 학생이 그중 하나만 신청할 수 있다. null이면 배타 그룹 없음. */
    @Column(name = "benefit_group_code", length = 50) private String benefitGroupCode;
    /** 자격 판정 시 합산할 연속 연도 수(학생의 입학년도부터 합산). 1이면 단일 연도 합산(기존 동작). */
    @Column(name = "cumulative_years", nullable = false) private Integer cumulativeYears = 1;
    /** true면 minimumPoints "이상"이 아니라 정확히 일치해야 자격을 충족한다. */
    @Column(name = "requires_exact_points", nullable = false) private boolean requiresExactPoints = false;

    public static MileageBenefitPolicy create(
            String benefitType,
            String semesterCode,
            String benefitName,
            BigDecimal minimumPoints,
            BigDecimal benefitAmount,
            JsonNode criteriaData,
            Instant applicationStartsAt,
            Instant applicationEndsAt,
            Integer createdBy,
            String benefitGroupCode,
            Integer cumulativeYears,
            boolean requiresExactPoints
    ) {
        MileageBenefitPolicy policy = new MileageBenefitPolicy();
        policy.benefitType = benefitType;
        policy.semesterCode = semesterCode;
        policy.benefitName = benefitName;
        policy.minimumPoints = minimumPoints;
        policy.benefitAmount = benefitAmount;
        policy.criteriaData = criteriaData;
        policy.applicationStartsAt = applicationStartsAt;
        policy.applicationEndsAt = applicationEndsAt;
        policy.createdBy = createdBy;
        policy.benefitGroupCode = benefitGroupCode;
        policy.cumulativeYears = cumulativeYears == null ? 1 : cumulativeYears;
        policy.requiresExactPoints = requiresExactPoints;
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
