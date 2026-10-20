package com.gnagnoohc.scms.domain.career.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;

/**
 * 협약기업 단건 상세 정보 응답 DTO
 *
 * <p>기업 상세 모달, 수정 화면 및 채용공고 연계 시 기업 메타데이터 제공
 * 심사 교직원 식별자({@code verifiedBy}) 및 승인 일시 포함</p>
 *
 * <ul>
 *   <li>{@code address}: 본사/사업장 소재지 주소</li>
 *   <li>{@code verifiedBy}: 승인/반려 처리를 수행한 교직원 PK (app_user.user_id)</li>
 *   <li>{@code verifiedAt}: 교직원 심사 완료 일시 (KST)</li>
 * </ul>
 *
 * @author YUN
 */
@Getter
@Builder
@Schema(description = "기업 상세 정보 응답 DTO")
public class CompanyDetailResponseDTO {

    @Schema(description = "기업 PK 식별자", example = "1")
    private Integer companyAccountId;

    @Schema(description = "사업자등록번호", example = "120-81-47521")
    private String businessRegistrationNo;

    @Schema(description = "기업 공식 명칭", example = "(주)카카오")
    private String companyName;

    @Schema(description = "대표자명", example = "홍길동")
    private String representativeName;

    @Schema(description = "소재지 주소", example = "제주특별자치도 제주시 첨단로 242")
    private String address;

    @Schema(description = "담당자 성명", example = "김담당")
    private String contactName;

    @Schema(description = "담당자 이메일", example = "hr_career@kakao.com")
    private String contactEmail;

    @Schema(description = "담당자 전화번호", example = "02-1234-5678")
    private String contactPhone;

    @Schema(description = "기업 승인 상태", example = "VERIFIED")
    private String verificationStatus;

    @Schema(description = "검증 교직원 PK 식별자", example = "5")
    private Integer verifiedBy;

    @Schema(description = "승인/검증 일시 (KST)")
    private OffsetDateTime verifiedAt;

    @Schema(description = "계정 활성 상태", example = "ACTIVE")
    private String accountStatus;

    @Schema(description = "가입 신청 일시 (KST)")
    private OffsetDateTime createdAt;

    @Schema(description = "최종 정보 수정 일시 (KST)")
    private OffsetDateTime updatedAt;
}