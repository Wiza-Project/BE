package com.gnagnoohc.scms.domain.career.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 채용공고 목록 조회 요약(카드/테이블 뷰) 응답 DTO
 *
 * <p><strong>[역할 및 데이터 구성 기준]</strong></p>
 * <ul>
 *   <li>채용공고 목록/게시판 페이징 조회용 경량 DTO</li>
 *   <li>네트워크 및 메모리 최적화를 위해 본문(TEXT)과 JSON 데이터는 제외,
 *   카드 노출용 기본 정보 및 매핑된 명칭(지역명, 직무명 등)만 전달</li>
 * </ul>
 */

@Getter
@Builder
@Schema(description = "채용공고 목록 요약 응답 DTO")
public class JobPostingSummaryResponseDTO {

    @Schema(description = "채용공고 식별자 (job_posting_id)", example = "10")
    private Integer jobPostingId;

    @Schema(description = "기업 계정 참조 식별자 (company_account_id)", example = "1")
    private Integer companyAccountId;

    @Schema(description = "기업명 (company_account 조인)", example = "(주)위자테크")
    private String companyName;

    @Schema(description = "NCS 직무명 (common_code 조인)", example = "정보기술 > SW개발")
    private String ncsCodeName;

    @Schema(description = "근무 지역명 (common_code 조인)", example = "서울시 강남구")
    private String regionCodeName;

    @Schema(description = "공고 제목 (posting_title)", example = "2026 하반기 Java 백엔드 신입 개발자 채용")
    private String postingTitle;

    @Schema(description = "고용 형태 (employment_type)", example = "정규직")
    private String employmentType;

    @Schema(description = "급여 조건 (salary_text)", example = "연봉 4,000만원 이상")
    private String salaryText;

    @Schema(description = "신청 시작 일시 (application_starts_at)", example = "2026-09-01T09:00:00+09:00")
    private OffsetDateTime applicationStartsAt;

    @Schema(description = "신청 종료 일시 (application_ends_at)", example = "2026-09-30T18:00:00+09:00")
    private OffsetDateTime applicationEndsAt;

    @Schema(description = "공고 유형 (posting_type)", example = "GENERAL")
    private String postingType;

    @Schema(description = "검수 상태 (review_status)", example = "APPROVED")
    private String reviewStatus;

    @Schema(description = "게시 상태 (posting_status)", example = "PUBLISHED")
    private String postingStatus;
}