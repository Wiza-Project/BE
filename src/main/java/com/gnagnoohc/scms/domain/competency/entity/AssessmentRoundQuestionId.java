package com.gnagnoohc.scms.domain.competency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter @Embeddable @EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssessmentRoundQuestionId implements Serializable {
    @Column(name = "assessment_round_id", nullable = false) private Integer assessmentRoundId;
    @Column(name = "question_id", nullable = false) private Integer questionId;
}
