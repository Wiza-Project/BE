package com.gnagnoohc.scms.domain.competency.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity @Getter
@Table(name = "assessment_score", uniqueConstraints = @UniqueConstraint(
        name = "uq_assessment_score_attempt_competency", columnNames = {"attempt_id", "competency_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssessmentScore {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assessment_score_id", nullable = false) private Integer assessmentScoreId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "attempt_id", nullable = false) private AssessmentAttempt attempt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "competency_id", nullable = false) private Competency competency;
    @Column(name = "raw_score", nullable = false, precision = 10, scale = 2) private BigDecimal rawScore;
    @Column(name = "converted_score", nullable = false, precision = 10, scale = 2) private BigDecimal convertedScore;
    @Column(name = "percentile", precision = 6, scale = 3) private BigDecimal percentile;
    @Column(name = "calculated_at", nullable = false) private Instant calculatedAt = Instant.now();
}
