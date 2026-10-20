package com.gnagnoohc.scms.global.common.ncs;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * [임베딩처리 전용]
 * NCS 분류(+직무) 기준정보 조회 API 응답 아이템 레코드
 * 대중소세분류 코드와 코드명 포함, 직무 상세 설명 컬럼 추가한 9가지 임베딩 전용 아이템
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NcsSubItem(
        @JsonProperty("NCS_LCLAS_CD") String largeCategoryCode,       // 대분류 코드 (2자리)
        @JsonProperty("NCS_LCLAS_CDNM") String largeCategoryName,     // 대분류명
        @JsonProperty("NCS_MCLAS_CD") String mediumCategoryCode,      // 중분류 코드 (4자리)
        @JsonProperty("NCS_MCLAS_CDNM") String mediumCategoryName,    // 중분류명
        @JsonProperty("NCS_SCLAS_CD") String smallCategoryCode,       // 소분류 코드 (6자리)
        @JsonProperty("NCS_SCLAS_CDNM") String smallCategoryName,     // 소분류명
        @JsonProperty("NCS_SUBD_CD") String subCategoryCode,          // 세분류 직무 코드 (8자리)
        @JsonProperty("NCS_SUBD_CDNM") String subCategoryName,        // 직무명
        @JsonAlias({"DUTY_DEF", "NCS_DEFN"})
        @JsonProperty("DUTY_DEF") String jobDescription) {
}