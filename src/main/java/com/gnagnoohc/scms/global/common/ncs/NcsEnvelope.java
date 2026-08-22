package com.gnagnoohc.scms.global.common.ncs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * NCS 기준정보 조회 API(공공데이터포털 B490007/hrdkapi) 공통 응답 봉투.
 *
 * <p>NCS001(대분류)~NCS007(능력단위키워드 검색) 7개 오퍼레이션이 전부 이 구조
 * (header/body/items/item, 동일한 페이징 필드)를 그대로 쓴다 — 오퍼레이션마다 다른 건
 * item 안의 필드뿐이라 item 타입만 제네릭으로 뺐다. 지금은 {@link NcsLclasItem}(대분류)만
 * 쓰지만, 향후 세분류(NCS004)·능력단위(NCS005/006) 연동이 필요해지면 item 레코드만
 * 새로 추가하고 이 봉투/{@link NcsApiClient#fetchAll}은 그대로 재사용하면 된다.</p>
 */
//TODO: 중/소/세 분류 가져올 시 item 추가를 통해 재사용
@JsonIgnoreProperties(ignoreUnknown = true)
public record NcsEnvelope<T>(Response<T> response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response<T>(Header header, Body<T> body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Header(String resultCode, String resultMsg) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Body<T>(Items<T> items, int totalCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Items<T>(@JsonProperty("item") List<T> item) {
    }
}
