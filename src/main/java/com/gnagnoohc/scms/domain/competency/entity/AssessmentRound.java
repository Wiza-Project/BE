package com.gnagnoohc.scms.domain.competency.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity @Getter
@Table(name = "assessment_round", uniqueConstraints = @UniqueConstraint(
        name = "uq_assessment_round_period_type", columnNames = {"academic_year", "semester_code", "assessment_type"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssessmentRound extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assessment_round_id", nullable = false) private Integer assessmentRoundId;
    @Column(name = "assessment_name", nullable = false, length = 200) private String assessmentName;
    @Column(name = "academic_year", nullable = false) private Integer academicYear;
    @Column(name = "semester_code", nullable = false, length = 20) private String semesterCode;
    @Column(name = "assessment_type", nullable = false, length = 20) private String assessmentType;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "target_condition", columnDefinition = "jsonb") private JsonNode targetCondition;
    @Column(name = "round_status", nullable = false, length = 20) private String roundStatus = "DRAFT";
    @Column(name = "created_by", nullable = false) private Integer createdBy;
}
