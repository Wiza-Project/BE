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

    // 회차 개설 시 문항을 편성한다. displayOrder는 회차 안에서 1부터 이어지는 응답 순서다.
    // 복합키(id)는 @MapsId로 두 연관관계에서 파생되므로 여기서 직접 만들지 않는다 —
    // 다만 round는 이미 저장돼 식별자가 있어야 한다.
    public static AssessmentRoundQuestion of(AssessmentRound assessmentRound, AssessmentQuestion question,
                                             int displayOrder, Integer createdBy) {
        AssessmentRoundQuestion roundQuestion = new AssessmentRoundQuestion();
        roundQuestion.id = new AssessmentRoundQuestionId(
                assessmentRound.getAssessmentRoundId(), question.getQuestionId());
        roundQuestion.assessmentRound = assessmentRound;
        roundQuestion.question = question;
        roundQuestion.displayOrder = displayOrder;
        roundQuestion.createdBy = createdBy;
        return roundQuestion;
    }
}
