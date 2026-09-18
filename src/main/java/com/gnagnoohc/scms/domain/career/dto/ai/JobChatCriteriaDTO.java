package com.gnagnoohc.scms.domain.career.dto.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/** LLM이 자연어에서 추출한 검색 조건. */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "LLM이 추출한 채용공고 검색 조건")
public class JobChatCriteriaDTO {

    @Schema(description = "희망 지역", example = "서울")
    private String region;

    @Schema(description = "NCS 직무 분류 또는 직무명", example = "정보기술")
    private String ncs;

    @Schema(description = "고용 형태", example = "정규직")
    private String employmentType;

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    @Schema(description = "검색 키워드", example = "[Java, Spring, 백엔드]")
    private List<String> keywords = new ArrayList<>();

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getNcs() { return ncs; }
    public void setNcs(String ncs) { this.ncs = ncs; }
    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }
    public List<String> getKeywords() { return keywords == null ? List.of() : keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords == null ? new ArrayList<>() : keywords; }
}
