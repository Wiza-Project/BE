package com.gnagnoohc.scms.domain.competency.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity @Getter
@Table(name = "assessment_response", uniqueConstraints = @UniqueConstraint(
        name = "uq_assessment_response_attempt_question", columnNames = {"attempt_id", "question_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssessmentResponse {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "response_id", nullable = false) private Integer responseId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "attempt_id", nullable = false) private AssessmentAttempt attempt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "question_id", nullable = false) private AssessmentQuestion question;
    @Column(name = "selected_value", nullable = false, precision = 10, scale = 2) private BigDecimal selectedValue;
    @Column(name = "saved_at", nullable = false) private Instant savedAt = Instant.now();
    @Column(name = "created_by", nullable = false) private Integer createdBy;
}
