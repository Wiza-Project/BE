package com.gnagnoohc.scms.domain.counsel.dto.response;

import java.util.List;

/**
 * 활성 스트레스 문항 조회 응답이다. 엔티티(PsychologicalTestQuestion)를 그대로 반환하지 않고
 * 공개해도 되는 필드만 이 DTO로 옮겨 담는다.
 * optionData를 Jackson 2 JsonNode 그대로 노출하지 않는 이유는 AssessmentQuestionResponse.ResponseOption과
 * 같다: Spring Boot 4의 HTTP 직렬화는 Jackson 3(tools.jackson) ObjectMapper를 쓰는데, 별개 패키지인
 * Jackson 2 JsonNode를 트리 노드로 인식하지 못하고 평범한 빈(bean)으로 오인해 isArray() 같은 public
 * 메서드를 getter로 잘못 직렬화한다. 그래서 평범한 record 리스트로 명시적으로 변환해 담는다.
 */
public record StressTestQuestionsResponse(
        String testType,
        String testVersion,
        String instruction,
        List<Question> questions
) {
    public record Question(
            Long questionId,
            Integer questionNo,
            String questionText,
            List<Option> optionData
    ) {
    }

    public record Option(Integer value, String label) {
    }
}
