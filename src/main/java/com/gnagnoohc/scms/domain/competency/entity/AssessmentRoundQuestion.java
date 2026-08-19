package com.gnagnoohc.scms.domain.competency.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Getter
@Table(name = "assessment_round_question", uniqueConstraints = @UniqueConstraint(
        name = "uq_assessment_round_question_order", columnNames = {"assessment_round_id", "display_order"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssessmentRoundQuestion {
    @EmbeddedId private AssessmentRoundQuestionId id;
    @MapsId("assessmentRoundId") @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_round_id", nullable = false) private AssessmentRound assessmentRound;
    @MapsId("questionId") @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false) private AssessmentQuestion question;
    @Column(name = "display_order", nullable = false) private Integer displayOrder;
    @Column(name = "created_by", nullable = false) private Integer createdBy;
}
