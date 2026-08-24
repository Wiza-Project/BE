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

    public static AssessmentResponse create(AssessmentAttempt attempt, AssessmentQuestion question,
                                              BigDecimal selectedValue, Integer createdBy) {
        AssessmentResponse response = new AssessmentResponse();
        response.attempt = attempt;
        response.question = question;
        response.selectedValue = selectedValue;
        response.createdBy = createdBy;
        return response;
    }

    // 같은 문항에 재응답(중도저장 갱신)할 때 값과 저장 시각만 갱신한다.
    public void updateSelectedValue(BigDecimal selectedValue) {
        this.selectedValue = selectedValue;
        this.savedAt = Instant.now();
    }
}
