package com.gnagnoohc.scms.domain.competency.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentAttempt;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentRoundQuestion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record AssessmentResumeResponse(
        Integer attemptId,
        String attemptStatus,
        AssessmentResponseProgress progress,
        List<Item> items
) {
    // Jackson2 JsonNode(response_options)를 컨트롤러 응답에 직접 노출하면 Jackson3 HTTP 직렬화가 깨진다
    // (AssessmentQuestionResponse 참고) — 여기서도 평범한 record 리스트로 명시적으로 변환한다.
    public record ResponseOption(Integer value, String label) {}

    public record Item(
            Integer questionId,
            Integer competencyId,
            String competencyName,
            Integer displayOrder,
            String questionText,
            List<ResponseOption> responseOptions,
            BigDecimal selectedValue
    ) {}

    public static AssessmentResumeResponse from(AssessmentAttempt attempt, List<AssessmentRoundQuestion> roundQuestions,
                                                  Map<Integer, BigDecimal> selectedValuesByQuestionId) {
        List<Item> items = roundQuestions.stream()
                .map(rq -> new Item(
                        rq.getQuestion().getQuestionId(),
                        rq.getQuestion().getCompetency().getCompetencyId(),
                        rq.getQuestion().getCompetency().getCompetencyName(),
                        rq.getDisplayOrder(),
                        rq.getQuestion().getQuestionText(),
                        toResponseOptions(rq.getQuestion().getResponseOptions()),
                        selectedValuesByQuestionId.get(rq.getQuestion().getQuestionId())
                ))
                .toList();

        int answeredCount = (int) items.stream().filter(item -> item.selectedValue() != null).count();
        AssessmentResponseProgress progress = new AssessmentResponseProgress(answeredCount, items.size());

        return new AssessmentResumeResponse(attempt.getAttemptId(), attempt.getAttemptStatus(), progress, items);
    }

    private static List<ResponseOption> toResponseOptions(JsonNode responseOptions) {
        List<ResponseOption> options = new ArrayList<>();
        if (responseOptions != null) {
            responseOptions.forEach(node ->
                    options.add(new ResponseOption(node.get("value").asInt(), node.get("label").asText())));
        }
        return options;
    }
}
