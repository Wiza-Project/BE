package com.gnagnoohc.scms.global.common.ncs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * NCS 대분류 API(NCS001) 응답 item 매핑.
 *
 * <p>{@code code}는 공공데이터포털이 내려주는 원본 NCS 대분류코드("01" ~ "24")이고,
 * 우리 common_code의 code(NC100, NC200 ...)와는 다르다 — 원본 코드는 추적용으로
 * common_code.description에 그대로 보존한다. {@link NcsCodeWriter} 참고.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NcsLclasItem(
        @JsonProperty("NCS_LCLAS_CD") String code,
        @JsonProperty("NCS_LCLAS_CDNM") String name,
        @JsonProperty("USG_YN") String usageYn
) {
}
