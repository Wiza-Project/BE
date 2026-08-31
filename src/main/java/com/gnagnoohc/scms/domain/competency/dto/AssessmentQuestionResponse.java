package com.gnagnoohc.scms.domain.competency.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.competency.entity.AssessmentQuestion;

import java.util.List;

public record AssessmentQuestionResponse(
        Integer questionId,
        Integer competencyId,
        String competencyName,
        Integer previousQuestionId,
        Integer versionNo,
        String questionText,
        boolean reverse,
        List<ResponseOption> responseOptions,
        boolean active
) {
    /**
     * JPA/Hibernate가 response_options(jsonb)를 채울 때 쓰는 Jackson 2 JsonNode를 그대로 응답에 노출하면 안 된다.
     * Spring Boot 4의 HTTP 직렬화는 Jackson 3(tools.jackson) ObjectMapper를 쓰는데, 이건 별개 패키지인
     * Jackson 2 JsonNode를 트리 노드로 인식하지 못하고 평범한 빈(bean)으로 오인해 isArray()/getNodeType() 같은
     * public 메서드를 getter로 잘못 직렬화한다. 그래서 여기서 평범한 record 리스트로 명시적으로 변환한다.
     */
    public record ResponseOption(Integer value, String label) {}

    public static AssessmentQuestionResponse from(AssessmentQuestion question) {
        return new AssessmentQuestionResponse(
                question.getQuestionId(),
                question.getCompetency().getCompetencyId(),
                question.getCompetency().getCompetencyName(),
                question.getPreviousQuestion() != null ? question.getPreviousQuestion().getQuestionId() : null,
                question.getVersionNo(),
                question.getQuestionText(),
                question.isReverse(),
                toResponseOptions(question.getResponseOptions()),
                question.isActive()
        );
    }

    private static List<ResponseOption> toResponseOptions(JsonNode responseOptions) {
        List<ResponseOption> options = new java.util.ArrayList<>();
        if (responseOptions != null) {
            responseOptions.forEach(node ->
                    options.add(new ResponseOption(node.get("value").asInt(), node.get("label").asText())));
        }
        return options;
    }
}
