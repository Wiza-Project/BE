package com.gnagnoohc.scms.domain.competency.dto.response;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

// responseOptions가 엔티티 저장용 Jackson 2 JsonNode 그대로 응답에 노출되면, 실제 HTTP 직렬화에 쓰이는
// Jackson 3 ObjectMapper가 이를 트리 노드로 인식하지 못하고 평범한 빈으로 오인해 isArray()/getNodeType() 같은
// 내부 getter를 그대로 찍어낸다. AssessmentQuestionResponse가 JsonNode 대신 record 리스트로 응답하는지 검증한다.
@SpringBootTest
class AssessmentQuestionResponseSerializationTest {

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void responseOptions_serializesAsPlainArray_notAsJsonNodeInternals() {
        AssessmentQuestionResponse response = new AssessmentQuestionResponse(
                1, 1, "자기관리 역량", null, 1, "문항", false,
                java.util.List.of(new AssessmentQuestionResponse.ResponseOption(1, "매우 그렇지 않다")),
                true);

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).contains("\"responseOptions\":[{\"value\":1,\"label\":\"매우 그렇지 않다\"}]");
        assertThat(json).doesNotContain("nodeType").doesNotContain("containerNode");
    }
}
