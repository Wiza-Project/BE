package com.gnagnoohc.scms.domain.mileage.support;

/**
 * 마일리지 도메인의 JSON 컬럼(detailData/duplicateRule/criteriaData)은 Hibernate의 jsonb
 * 포맷 매퍼가 Jackson 2(com.fasterxml.jackson.databind.JsonNode)에 고정되어 있어 엔티티에서는
 * Jackson 2 타입을 유지해야 하지만, Spring의 HTTP 바인딩은 Jackson 3(tools.jackson.databind)를
 * 사용한다. 두 JsonNode는 서로 무관한 타입이라 요청/응답 DTO와 엔티티 경계를 넘을 때 JSON 텍스트를
 * 거쳐 변환해야 한다.
 */
public final class MileageJsonNodeConverter {

    private static final com.fasterxml.jackson.databind.ObjectMapper JACKSON2 =
            new com.fasterxml.jackson.databind.ObjectMapper();
    private static final tools.jackson.databind.ObjectMapper JACKSON3 =
            new tools.jackson.databind.ObjectMapper();

    private MileageJsonNodeConverter() {
    }

    /** 응답 직렬화용: 엔티티에 저장된 Jackson2 JsonNode를 Jackson3 JsonNode로 변환한다. */
    public static tools.jackson.databind.JsonNode toJackson3(com.fasterxml.jackson.databind.JsonNode node) {
        return node == null ? null : JACKSON3.readTree(node.toString());
    }

    /** 엔티티 저장용: 요청 바디로 들어온 Jackson3 JsonNode를 Jackson2 JsonNode로 변환한다. */
    public static com.fasterxml.jackson.databind.JsonNode toJackson2(tools.jackson.databind.JsonNode node) {
        if (node == null) {
            return null;
        }
        try {
            return JACKSON2.readTree(node.toString());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("JSON 변환에 실패했습니다.", e);
        }
    }
}
