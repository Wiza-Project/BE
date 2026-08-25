package com.gnagnoohc.scms.domain.career.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 협약기업 기본 메타데이터 등록 및 제휴 신청 요청 DTO.
 *
 * <p>기업 사용자 또는 외부 접수 폼을 통해 협약기업 정보를 등록할 때 사용
 * 인증 체계(ID/PW) 배제, 추후 확장을 위해 엔티티는 고정으로 두며
 * 현재 로직 기준으로는 사업자등록번호 기반 식별 및 담당자 연락처 무결성에만 집중하는 요청 DTO</p>
 *
 * <ul>
 *   <li>{@code businessRegistrationNo}: 10자리 숫자 또는 하이픈 포맷의 사업자등록번호 (UK)</li>
 *   <li>{@code companyName}: 공식 법인/기업명</li>
 *   <li>{@code contactEmail}: 채용 공고 및 매칭 알림 수신용 이메일</li>
 * </ul>
 *
 * @author YUN
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "협약기업 정보 등록/신청 요청 DTO")
public class CompanyRegisterRequestDTO {

    @NotBlank(message = "사업자등록번호는 필수입니다.")
    @Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$|^\\d{10}$", message = "올바른 사업자등록번호 형식이어야 합니다.")
    @Schema(description = "사업자등록번호", example = "120-81-47521")
    private String businessRegistrationNo;

    @NotBlank(message = "기업명은 필수입니다.")
    @Schema(description = "기업체 공식 명칭", example = "(주)카카오")
    private String companyName;

    @NotBlank(message = "대표자명은 필수입니다.")
    @Schema(description = "대표자 성명", example = "홍길동")
    private String representativeName;

    @Schema(description = "소재지 주소", example = "제주특별자치도 제주시 첨단로 242")
    private String address;

    @NotBlank(message = "담당자 성명은 필수입니다.")
    @Schema(description = "채용 담당자 성명", example = "김담당")
    private String contactName;

    @NotBlank(message = "담당자 이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이어야 합니다.")
    @Schema(description = "채용 담당자 이메일", example = "hr_career@kakao.com")
    private String contactEmail;

    @NotBlank(message = "담당자 연락처는 필수입니다.")
    @Schema(description = "채용 담당자 직통 전화번호", example = "02-1234-5678")
    private String contactPhone;
}