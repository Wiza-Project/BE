package com.gnagnoohc.scms.domain.career.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 교직원/관리자 전용 협약기업 목록 다중 조건 검색 DTO
 *
 * <p>QueryDSL 동적 쿼리 바인딩 목적의 DTO (누락된 파라미터는 조건에서 제외 처리)</p>
 *
 * <ul>
 *   <li>{@code companyName}: 기업명 대소문자 무시 부분 일치 검색 (LIKE)</li>
 *   <li>{@code businessRegistrationNo}: 사업자등록번호 완전 일치 검색 (EQ)</li>
 *   <li>{@code verificationStatus}: 승인 상태 필터 (PENDING, VERIFIED, REJECTED)</li>
 *   <li>{@code accountStatus}: 계정 상태 필터 (ACTIVE, INACTIVE 등)</li>
 * </ul>
 *
 * @author YUN
 */
@Getter
@Setter
@Schema(description = "교직원/관리자 기업 목록 다중 검색 조건 DTO")
public class CompanySearchConditionDTO {

    @Schema(description = "기업명 키워드", example = "카카오")
    private String companyName;

    @Schema(description = "사업자등록번호", example = "120-81-47521")
    private String businessRegistrationNo;

    @Schema(description = "검증 상태", allowableValues = {"PENDING", "VERIFIED", "REJECTED"}, example = "PENDING")
    private String verificationStatus;

    @Schema(description = "계정 활성화 상태", allowableValues = {"ACTIVE", "INACTIVE", "LOCKED", "WITHDRAWN"}, example = "ACTIVE")
    private String accountStatus;
}