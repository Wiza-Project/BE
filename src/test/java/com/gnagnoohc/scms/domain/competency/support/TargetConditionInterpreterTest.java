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

    // grades가 배열이 아니면(예: 관리자가 단일 값을 실수로 저장) 조용히 "조건 없음"으로 넘기지 않고
    // 실패시켜야 한다 — 그렇지 않으면 전체 학생으로 잘못 집계된다(재검토 스레드 1번 근거).
    @Test
    void toPredicate_whenGradesNotArray_throwsInvalidFormat() {
        JsonNode condition = objectMapper.valueToTree(Map.of("grades", 3));

        assertThatThrownBy(() -> interpreter.toPredicate(condition))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_TARGET_CONDITION_INVALID_FORMAT);
    }

    // asInt()는 true를 1로 변환해버려서, 검증 없이 그대로 쓰면 "그럴듯하지만 틀린" 조건이
    // 에러 없이 만들어진다(재검토 스레드 4번 근거) — isIntegralNumber()로 미리 걸러야 한다.
    @Test
    void toPredicate_whenGradeElementIsBoolean_throwsInvalidFormat() {
        JsonNode condition = objectMapper.valueToTree(Map.of("grades", List.of(true)));

        assertThatThrownBy(() -> interpreter.toPredicate(condition))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_TARGET_CONDITION_INVALID_FORMAT);
    }

    // 4000.7처럼 소수점 값도 asInt()가 4000으로 잘라버려, 우연히 존재하는 다른 학과 코드ID와
    // 매칭될 수 있다 — 마찬가지로 등록 이전에 막아야 한다.
    @Test
    void toPredicate_whenMajorCodeIdElementIsDecimal_throwsInvalidFormat() {
        JsonNode condition = objectMapper.valueToTree(Map.of("majorCodeIds", List.of(4000.7)));

        assertThatThrownBy(() -> interpreter.toPredicate(condition))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_TARGET_CONDITION_INVALID_FORMAT);
    }

    // 4294971296 = 2^32 + 4000. isIntegralNumber()는 통과하지만 int로 캐스팅하면 오버플로우돼
    // 정확히 4000이 되어버린다 — 실제 존재하는 학과 코드ID 4000으로 조용히 둔갑할 수 있으므로
    // canConvertToInt()로 범위까지 확인해야 한다.
    @Test
    void toPredicate_whenMajorCodeIdElementExceedsIntRange_throwsInvalidFormat() {
        JsonNode condition = objectMapper.valueToTree(Map.of("majorCodeIds", List.of(4294971296L)));

        assertThatThrownBy(() -> interpreter.toPredicate(condition))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_TARGET_CONDITION_INVALID_FORMAT);
    }

    @Test
    void isValidShape_whenGradesAndMajorCodeIdsAreProperArrays_returnsTrue() {
        JsonNode condition = objectMapper.valueToTree(Map.of("grades", List.of(1, 2), "majorCodeIds", List.of(4000)));

        assertThat(interpreter.isValidShape(condition)).isTrue();
    }

    @Test
    void isValidShape_whenTargetConditionNull_returnsTrue() {
        assertThat(interpreter.isValidShape(null)).isTrue();
    }

    // grades/majorCodeIds가 아닌 키(예전 colleges 포함, 오타 등)는 조용히 무시하지 않고 예외로
    // 실패시킨다 — 이 학교는 단과대 자체가 없어 colleges를 특별 취급할 이유가 없으므로, 인식 못
    // 하는 키 전부를 하나의 규칙으로 막는다.
    @Test
    void toPredicate_whenUnrecognizedKeyPresent_throwsUnsupported() {
        JsonNode condition = objectMapper.valueToTree(Map.of("colleges", List.of("공과대학")));

        assertThatThrownBy(() -> interpreter.toPredicate(condition))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_TARGET_CONDITION_UNSUPPORTED);
    }

    @Test
    void toPredicate_whenTypoKeyPresent_throwsUnsupported() {
        JsonNode condition = objectMapper.valueToTree(Map.of("grade", List.of(3)));

        assertThatThrownBy(() -> interpreter.toPredicate(condition))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ASSESSMENT_TARGET_CONDITION_UNSUPPORTED);
    }

    @Test
    void hasUnrecognizedKey_whenOnlyKnownKeys_returnsFalse() {
        JsonNode condition = objectMapper.valueToTree(Map.of("grades", List.of(1), "majorCodeIds", List.of(4000)));

        assertThat(interpreter.hasUnrecognizedKey(condition)).isFalse();
    }
}
