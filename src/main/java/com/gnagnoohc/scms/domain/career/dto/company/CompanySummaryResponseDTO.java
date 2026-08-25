package com.gnagnoohc.scms.domain.career.dto.company;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;

/**
 * 협약기업 목록 조회 및 검색 결과 요약 응답 DTO
 *
 * <p>목록 테이블 그리드 렌더링에 필요한 핵심 메타데이터만 포함하며, 추후 기업 회원으로 기능 확장 시 데이터 추가 필요.
 * 시계열 데이터는 KST(+09:00) 기준 {@link java.time.OffsetDateTime}으로 직렬화 처리</p>
 *
 * <ul>
 *   <li>{@code companyAccountId}: 기업 고유 식별자 PK</li>
 *   <li>{@code verificationStatus}: 기업 승인 상태</li>
 *   <li>{@code createdAt}: 신청 일시 (KST)</li>
 * </ul>
 *
 * @author YUN
 */
@Getter
@Builder
@Schema(description = "기업 목록 요약 응답 DTO")
public class CompanySummaryResponseDTO {

    @Schema(description = "기업 PK 식별자", example = "1")
    private Integer companyAccountId;

    @Schema(description = "사업자등록번호", example = "120-81-47521")
    private String businessRegistrationNo;

    @Schema(description = "기업 공식 명칭", example = "(주)카카오")
    private String companyName;

    @Schema(description = "대표자명", example = "홍길동")
    private String representativeName;

    @Schema(description = "담당자 성명", example = "김담당")
    private String contactName;

    @Schema(description = "담당자 이메일", example = "hr_career@kakao.com")
    private String contactEmail;

    @Schema(description = "기업 승인 상태 (PENDING, VERIFIED, REJECTED)", example = "PENDING")
    private String verificationStatus;

    @Schema(description = "계정 활성 상태 (ACTIVE, INACTIVE 등)", example = "ACTIVE")
    private String accountStatus;

    @Schema(description = "신청 일시 (KST)")
    private OffsetDateTime createdAt;
}