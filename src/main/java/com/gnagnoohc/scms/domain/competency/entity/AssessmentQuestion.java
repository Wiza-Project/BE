package com.gnagnoohc.scms.domain.competency.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity @Getter
@Table(name = "assessment_question", uniqueConstraints = @UniqueConstraint(
        name = "uq_assessment_question_previous_version", columnNames = {"previous_question_id", "version_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssessmentQuestion extends BaseCreatedAtEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id", nullable = false) private Integer questionId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "competency_id", nullable = false) private Competency competency;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "previous_question_id") private AssessmentQuestion previousQuestion;
    @Column(name = "version_no", nullable = false) private Integer versionNo = 1;
    @Column(name = "question_text", nullable = false, columnDefinition = "text") private String questionText;
    @Column(name = "is_reverse", nullable = false) private boolean reverse = false;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "response_options", nullable = false, columnDefinition = "jsonb")
    private JsonNode responseOptions;
    @Column(name = "is_active", nullable = false) private boolean active = true;
    @Column(name = "created_by", nullable = false) private Integer createdBy;
}
