package com.gnagnoohc.scms.domain.mileage.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 활동 유형별·학기별 마일리지 적립 점수와 적용 기간을 관리한다. */
@Entity @Getter
@Table(name = "mileage_policy", uniqueConstraints = @UniqueConstraint(
        name = "uq_mileage_policy_activity_period_version",
        columnNames = {"activity_type_id", "academic_year", "semester_code", "version_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MileagePolicy extends BaseCreatedAtEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mileage_policy_id", nullable = false) private Integer mileagePolicyId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "activity_type_id", nullable = false) private MileageActivityType activityType;
    @Column(name = "academic_year", nullable = false) private Integer academicYear;
    @Column(name = "semester_code", nullable = false, length = 20) private String semesterCode = "ALL";
    @Column(name = "version_no", nullable = false) private Integer versionNo;
    @Column(name = "points", nullable = false, precision = 10, scale = 2) private BigDecimal points;
    @Column(name = "maximum_points", precision = 10, scale = 2) private BigDecimal maximumPoints;
    @Column(name = "valid_from", nullable = false) private LocalDate validFrom;
    @Column(name = "valid_to") private LocalDate validTo;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "duplicate_rule", columnDefinition = "jsonb") private JsonNode duplicateRule;
    @Column(name = "policy_status", nullable = false, length = 20) private String policyStatus = "ACTIVE";
    @Column(name = "created_by", nullable = false) private Integer createdBy;
}
