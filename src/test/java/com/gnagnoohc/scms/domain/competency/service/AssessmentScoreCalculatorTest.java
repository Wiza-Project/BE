package com.gnagnoohc.scms.domain.competency.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentQuestion;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRoundQuestion;
import com.gnagnoohc.scms.domain.competency.entity.Competency;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssessmentScoreCalculatorTest {

    private final AssessmentScoreCalculator calculator = new AssessmentScoreCalculator();

    private static <T> T newInstance(Class<T> type) throws ReflectiveOperationException {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Competency buildCompetency(Integer competencyId, String code) {
        return buildCompetency(competencyId, code, 1);
    }

    private static Competency buildCompetency(Integer competencyId, String code, Integer displayOrder) {
        Competency competency = Competency.createTop(code, code + "역량", null, null, displayOrder, 1);
        ReflectionTestUtils.setField(competency, "competencyId", competencyId);
        return competency;
    }

    private static AssessmentQuestion buildQuestion(Competency competency, Integer questionId, boolean reverse) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode options = mapper.createArrayNode();
        for (int value = 1; value <= 5; value++) {
            ObjectNode option = mapper.createObjectNode();
            option.put("value", value);
            option.put("label", value + "점");
            options.add(option);
        }
        AssessmentQuestion question = AssessmentQuestion.createFromUpload(competency, "문항", options, 1);
        ReflectionTestUtils.setField(question, "questionId", questionId);
        question.editInPlace(question.getQuestionText(), options, reverse);
        return question;
    }

    private static AssessmentRoundQuestion buildRoundQuestion(AssessmentQuestion question) throws ReflectiveOperationException {
        AssessmentRoundQuestion roundQuestion = newInstance(AssessmentRoundQuestion.class);
        ReflectionTestUtils.setField(roundQuestion, "question", question);
        return roundQuestion;
    }

    @Test
    void calculate_averagesResponsesAndConvertsToHundredPointScale() throws Exception {
        Competency competency = buildCompetency(1, "C1");
        AssessmentQuestion q1 = buildQuestion(competency, 1, false);
        AssessmentQuestion q2 = buildQuestion(competency, 2, false);

        List<AssessmentRoundQuestion> roundQuestions = List.of(buildRoundQuestion(q1), buildRoundQuestion(q2));
        Map<Integer, BigDecimal> selectedValues = Map.of(1, BigDecimal.valueOf(4), 2, BigDecimal.valueOf(2));

        List<AssessmentScoreCalculator.CompetencyScore> result = calculator.calculate(roundQuestions, selectedValues);

        assertThat(result).hasSize(1);
        AssessmentScoreCalculator.CompetencyScore score = result.get(0);
        assertThat(score.competency()).isEqualTo(competency);
        assertThat(score.rawScore()).isEqualByComparingTo(BigDecimal.valueOf(3)); // (4+2)/2
        assertThat(score.convertedScore()).isEqualByComparingTo(BigDecimal.valueOf(60)); // 3/5*100
    }

    @Test
    void calculate_reverseQuestion_invertsValueBeforeAveraging() throws Exception {
        Competency competency = buildCompetency(1, "C1");
        AssessmentQuestion reverseQuestion = buildQuestion(competency, 1, true);

        List<AssessmentRoundQuestion> roundQuestions = List.of(buildRoundQuestion(reverseQuestion));
        Map<Integer, BigDecimal> selectedValues = Map.of(1, BigDecimal.valueOf(2)); // 6-2=4로 역산되어야 함

        List<AssessmentScoreCalculator.CompetencyScore> result = calculator.calculate(roundQuestions, selectedValues);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).rawScore()).isEqualByComparingTo(BigDecimal.valueOf(4));
        assertThat(result.get(0).convertedScore()).isEqualByComparingTo(BigDecimal.valueOf(80)); // 4/5*100
    }

    @Test
    void calculate_groupsByCompetency_returnsOneScorePerCompetency() throws Exception {
        Competency competency1 = buildCompetency(1, "C1");
        Competency competency2 = buildCompetency(2, "C2");
        AssessmentQuestion c1q1 = buildQuestion(competency1, 1, false);
        AssessmentQuestion c2q1 = buildQuestion(competency2, 2, false);

        List<AssessmentRoundQuestion> roundQuestions = List.of(buildRoundQuestion(c1q1), buildRoundQuestion(c2q1));
        Map<Integer, BigDecimal> selectedValues = Map.of(1, BigDecimal.valueOf(5), 2, BigDecimal.valueOf(1));

        List<AssessmentScoreCalculator.CompetencyScore> result = calculator.calculate(roundQuestions, selectedValues);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(s -> s.competency().getCompetencyId()).containsExactlyInAnyOrder(1, 2);
    }

    // groupingBy는 HashMap 기반이라 competencyId/삽입 순서와 무관하게 뒤섞일 수 있다.
    // displayOrder를 competencyId 순서와 반대로 둬서, 우연히 competencyId 순서로 나온 것이 아니라
    // 실제로 displayOrder 기준 정렬이 적용됐는지 검증한다.
    @Test
    void calculate_ordersResultByCompetencyDisplayOrder_notByCompetencyIdOrInsertionOrder() throws Exception {
        Competency competencyDisplayedSecond = buildCompetency(1, "C1", 2);
        Competency competencyDisplayedFirst = buildCompetency(2, "C2", 1);
        AssessmentQuestion c1q1 = buildQuestion(competencyDisplayedSecond, 1, false);
        AssessmentQuestion c2q1 = buildQuestion(competencyDisplayedFirst, 2, false);

        List<AssessmentRoundQuestion> roundQuestions = List.of(buildRoundQuestion(c1q1), buildRoundQuestion(c2q1));
        Map<Integer, BigDecimal> selectedValues = Map.of(1, BigDecimal.valueOf(5), 2, BigDecimal.valueOf(1));

        List<AssessmentScoreCalculator.CompetencyScore> result = calculator.calculate(roundQuestions, selectedValues);

        assertThat(result).extracting(s -> s.competency().getCompetencyId()).containsExactly(2, 1);
    }
}
