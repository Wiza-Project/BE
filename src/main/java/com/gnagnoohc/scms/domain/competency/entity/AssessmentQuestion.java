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

    public static AssessmentQuestion createFromUpload(Competency competency, String questionText,
                                                        JsonNode responseOptions, Integer createdBy) {
        AssessmentQuestion question = new AssessmentQuestion();
        question.competency = competency;
        question.questionText = questionText;
        question.responseOptions = responseOptions;
        question.createdBy = createdBy;
        return question;
    }

    // 아직 응시 이력이 없는 문항만 제자리 수정 허용 (호출 전 응답 존재 여부는 서비스에서 판단)
    public void editInPlace(String questionText, JsonNode responseOptions, boolean reverse) {
        this.questionText = questionText;
        this.responseOptions = responseOptions;
        this.reverse = reverse;
    }

    // 이미 응시 이력이 있는 문항은 제자리 수정 대신 새 버전으로 대체한다 (연도별 추이 분석을 위해 이전 버전 보존)
    public static AssessmentQuestion createNewVersion(AssessmentQuestion previous, String questionText,
                                                        JsonNode responseOptions, boolean reverse, Integer createdBy) {
        AssessmentQuestion question = new AssessmentQuestion();
        question.competency = previous.competency;
        question.previousQuestion = previous;
        question.versionNo = previous.versionNo + 1;
        question.questionText = questionText;
        question.responseOptions = responseOptions;
        question.reverse = reverse;
        question.createdBy = createdBy;
        return question;
    }

    public void deactivate() {
        this.active = false;
    }
}
