package com.gnagnoohc.scms.domain.career.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Table(name = "student_job_relation", uniqueConstraints = @UniqueConstraint(
        name = "uq_student_job_relation_student_posting",
        columnNames = {"student_id", "job_posting_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudentJobRelation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "student_job_relation_id", nullable = false)
    private Integer studentJobRelationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_consent_id", nullable = true)
    private UserConsent userConsent;

    @Column(name = "matching_score", nullable = true, precision = 6, scale = 2)
    private BigDecimal matchingScore;

    @Column(name = "matching_status", nullable = true, length = 20)
    private String matchingStatus;

    @Column(name = "matched_at", nullable = true)
    private Instant matchedAt;

    @Column(name = "bookmarked_at", nullable = true)
    private Instant bookmarkedAt;

    @Column(name = "applied_at", nullable = true)
    private Instant appliedAt;

    @Column(name = "canceled_at", nullable = true)
    private Instant canceledAt;

    @Column(name = "recommendation_source", nullable = true, length = 30)
    private String recommendationSource;

    @Column(name = "application_status", nullable = true, length = 20)
    private String applicationStatus;

    @Column(name = "selection_stage", nullable = true, length = 50)
    private String selectionStage;

    @Column(name = "selection_result", nullable = true, length = 30)
    private String selectionResult;
}
