package com.gnagnoohc.scms.domain.competency.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TargetConditionInterpreterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TargetConditionInterpreter interpreter = new TargetConditionInterpreter();

    @Test
    void toPredicate_whenTargetConditionNull_returnsNull() {
        assertThat(interpreter.toPredicate(null)).isNull();
    }

    @Test
    void toPredicate_whenGradesOnly_buildsGradePredicate() {
        JsonNode condition = objectMapper.valueToTree(Map.of("grades", List.of(3)));

        BooleanExpression predicate = interpreter.toPredicate(condition);

        assertThat(predicate).isNotNull();
        assertThat(predicate.toString()).contains("studentAcademicDetail.grade = 3");
    }

    // QueryDSL은 .in()에 값이 여러 개일 때만 "in [...]"로 렌더링하고, 단일 값이면 "="로
    // 최적화한다 — 여러 학년을 동시에 대상으로 지정하는 실제 사용 사례를 검증한다.
    @Test
    void toPredicate_whenMultipleGrades_buildsInPredicate() {
        JsonNode condition = objectMapper.valueToTree(Map.of("grades", List.of(1, 3)));

        BooleanExpression predicate = interpreter.toPredicate(condition);

        assertThat(predicate).isNotNull();
        assertThat(predicate.toString()).contains("studentAcademicDetail.grade in [1, 3]");
    }

    @Test
    void toPredicate_whenMajorCodeIdsOnly_buildsMajorCodeIdPredicate() {
        JsonNode condition = objectMapper.valueToTree(Map.of("majorCodeIds", List.of(8000)));

        BooleanExpression predicate = interpreter.toPredicate(condition);

        assertThat(predicate).isNotNull();
        assertThat(predicate.toString()).contains("codeId = 8000");
    }

    @Test
    void toPredicate_whenGradesAndMajorCodeIds_combinesWithAnd() {
        JsonNode condition = objectMapper.valueToTree(Map.of("grades", List.of(2), "majorCodeIds", List.of(4000)));

        BooleanExpression predicate = interpreter.toPredicate(condition);

        assertThat(predicate).isNotNull();
        assertThat(predicate.toString())
                .contains("studentAcademicDetail.grade = 2")
                .contains("codeId = 4000");
    }

    // DB CHECK 제약(1~4) 밖 값은 조건을 무시하지 않고 항상 불일치로 고정해야 한다
    // (null=조건없음과 잘못된 값을 구분하기 위함, AcademicRecordQueryRepository.gradeEq와 동일한 근거).
    @Test
    void toPredicate_whenGradeOutOfRange_alwaysFalse() {
        JsonNode condition = objectMapper.valueToTree(Map.of("grades", List.of(9)));

        BooleanExpression predicate = interpreter.toPredicate(condition);

        assertThat(predicate).isEqualTo(Expressions.FALSE);
    }

    @Test
    void toPredicate_whenEmptyObject_returnsNull() {
        JsonNode condition = objectMapper.valueToTree(Map.of());

        assertThat(interpreter.toPredicate(condition)).isNull();
    }

    // colleges(단과대)는 학적 데이터에 대응하는 계층이 없어 조용히 무시하지 않고 예외로 실패시킨다.
    @Test
    void toPredicate_whenCollegesPresent_throwsUnsupported() {
        JsonNode condition = objectMapper.valueToTree(Map.of("colleges", List.of("공과대학")));

        assertThatThrownBy(() -> interpreter.toPredicate(condition))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_TARGET_CONDITION_UNSUPPORTED);
    }
}
