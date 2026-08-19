package com.gnagnoohc.scms.domain.counsel.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Table(name = "psychological_test_question", uniqueConstraints = @UniqueConstraint(
        name = "uq_psychological_test_question_type_version_no",
        columnNames = {"test_type", "test_version", "question_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PsychologicalTestQuestion extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "psychological_test_question_id", nullable = false)
    private Integer psychologicalTestQuestionId;

    @Column(name = "test_type", nullable = false, length = 40)
    private String testType;

    @Column(name = "test_version", nullable = false, length = 30)
    private String testVersion;

    @Column(name = "question_no", nullable = false)
    private Integer questionNo;

    @Column(name = "question_text", nullable = false, columnDefinition = "text")
    private String questionText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_options", nullable = false, columnDefinition = "jsonb")
    private JsonNode responseOptions;
}
