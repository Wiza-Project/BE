package com.gnagnoohc.scms.domain.counsel.entity;

import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
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

@Entity
@Getter
@Table(name = "psychological_test_answer", uniqueConstraints = @UniqueConstraint(
        name = "uq_psychological_test_answer_result_question",
        columnNames = {"psychological_test_result_id", "psychological_test_question_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PsychologicalTestAnswer extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "psychological_test_answer_id", nullable = false)
    private Integer psychologicalTestAnswerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "psychological_test_result_id", nullable = false)
    private PsychologicalTestResult psychologicalTestResult;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "psychological_test_question_id", nullable = false)
    private PsychologicalTestQuestion psychologicalTestQuestion;

    @Column(name = "selected_value", nullable = false)
    private Integer selectedValue;
}
