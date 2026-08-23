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

    public static AssessmentRound create(String assessmentName, Integer academicYear, String semesterCode,
                                          String assessmentType, Instant startsAt, Instant endsAt,
                                          JsonNode targetCondition, Integer createdBy) {
        AssessmentRound round = new AssessmentRound();
        round.assessmentName = assessmentName;
        round.academicYear = academicYear;
        round.semesterCode = semesterCode;
        round.assessmentType = assessmentType;
        round.startsAt = startsAt;
        round.endsAt = endsAt;
        round.targetCondition = targetCondition;
        round.createdBy = createdBy;
        return round;
    }

    // 응시가 시작된 회차는 서비스 계층에서 전면 차단 후 호출하므로, 여기서는 잠금 여부를 다시 판단하지 않는다.
    public void update(String assessmentName, Integer academicYear, String semesterCode,
                        String assessmentType, Instant startsAt, Instant endsAt, JsonNode targetCondition) {
        this.assessmentName = assessmentName;
        this.academicYear = academicYear;
        this.semesterCode = semesterCode;
        this.assessmentType = assessmentType;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.targetCondition = targetCondition;
    }
}
