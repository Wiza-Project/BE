package com.gnagnoohc.scms.domain.career.dto.posting;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 채용공고 다중 조건 검색 파라미터 DTO
 *
 * <p><strong>[데이터 처리 및 쿼리 파라미터 매핑 기준]</strong></p>
 * <ul>
 *   <li><b>공통코드 식별자 (ncsCodeId, regionCodeId):</b> 클라이언트(화면)로부터 전달받는 공통코드 테이블(common_code)의 PK 값 -> 미선택 시 NULL 허용 (동적 조건에서 무시 처리)</li>
 *   <li><b>기업명 키워드 (companyName):</b> 기업 테이블(company_account)의 기업명 부분 일치 검색(containsIgnoreCase)에 활용 -> 빈 값/공백 입력 시 NULL 처리</li>
 *   <li><b>공고 구분 (postingType):</b> RECOMMENDED(추천), CAMPUS(교내), GENERAL(일반), INTERNSHIP(현장실습), JOB_FAIR(박람회) 등 카테고리 탭 필터링</li>
 *   <li><b>검수 상태 (reviewStatus):</b> 교직원/관리자 전용 조회 조건 (REQUESTED: 검수대기, APPROVED: 승인, REJECTED: 반려)</li>
 *   <li><b>동적 다중 필터링:</b> 모든 필드는 Optional(선택 조건)이며, 값이 존재하는 필드만 QueryDSL {@code BooleanExpression}을 통해 WHERE 절에 동적으로 바인딩됨</li>
 * </ul>
 *
 * @author YUN
 */
@Getter
@Setter
@Schema(description = "채용공고 다중 조건 검색 파라미터")
public class JobPostingSearchConditionDTO {

    @Schema(description = "NCS 직무 공통코드 식별자", example = "101")
    private Integer ncsCodeId;

    @Schema(description = "지역 공통코드 식별자", example = "201")
    private Integer regionCodeId;

    @Schema(description = "기업명 검색 키워드", example = "네이버")
    private String companyName;

    @Schema(description = "고용 형태", example = "정규직")
    private String employmentType;

    @Schema(description = "공고 구분 탭 (RECOMMENDED, CAMPUS, GENERAL, INTERNSHIP, JOB_FAIR)", example = "RECOMMENDED")
    private String postingType;

    @Schema(description = "검수 상태 (교직원 전용: REQUESTED, APPROVED, REJECTED)", example = "REQUESTED")
    private String reviewStatus;
}