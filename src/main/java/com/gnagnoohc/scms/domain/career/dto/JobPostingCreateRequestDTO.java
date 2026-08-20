package com.gnagnoohc.scms.domain.career.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 채용 공고 신규 등록 요청 DTO
 *
 * <p><strong>[데이터 처리 및 넘버링 설계 기준]</strong></p>
 * <ul>
 *   <li><b>채용 공고 식별자 (job_posting_id):</b> 등록 시점에는 DB 영속화 전이므로 PK가 존재하지 않음 -> DB의 IDENTITY(Auto Increment) 속성으로 자동 처리</li>
 *   <li><b>기업 계정 식별자 (company_account_id):</b> 기업 계정의 화면은 존재하지 않음 -> 교직원이 사전 등록된 기업 계정 목록에서 선택한 식별자 pk를 전달</li>
 *   <li><b>공통코드 식별자 (ncs_code_id, region_code_id):</b> ERD 제약조건상 NULL 허용</li>
 *   <li><b>게시/검수 상태:</b> 등록 시 기본값({@code review_status='REQUESTED'}, {@code posting_status='DRAFT'})으로 서버 및 DB에서 자동 초기화</li>
 * </ul>
 *
 * @author YUN
 */

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Schema(description = "채용 공고 신규 등록 요청 DTO")
public class JobPostingCreateRequestDTO {
    @NotNull(message = "기업 계정 번호는 필수입니다.")
    @Schema(description = "기업 계정 식별자 (company_account_id)", example = "1")
    private Integer companyAccountId;

    @Schema(description = "common_code 테이블의 PK (NCS 소분류 코드 매핑)", example = "ncs코드처리후예시추가")
    private Integer ncsCodeId;

    @Schema(description = "common_code 테이블의 PK (지역 분류 코드 매핑)", example = "지역코드처리후예시추가")
    private Integer regionCodeId;

    @NotBlank(message = "공고 제목은 필수입니다.")
    @Schema(description = "공고 제목 (posting_title)", example = "2026 하반기 Java 백엔드 신입 개발자 채용")
    private String postingTitle;

    @NotBlank(message = "직무 설명은 필수입니다.")
    @Schema(description = "직무 설명 (job_description, TEXT 타입)", example = "Spring Boot 기반 REST API 개발 및 DB 모델링")
    private String jobDescription;

    @Schema(description = "모집 인원 (recruitment_count, Integer)", example = "2")
    private Integer recruitmentCount;

    @Schema(description = "고용 형태 (employment_type, VARCHAR(30))", example = "정규직")
    private String employmentType;

    @Schema(description = "급여 조건 (salary_text, VARCHAR(100))", example = "연봉 3,000만원 이상")
    private String salaryText;

    @Schema(description = "지원 자격 데이터 (qualification_data, JSONB)", example = "{\"major\": \"컴퓨터공학\", \"minGpa\": 3.5}")
    private Map<String, Object> qualificationData;

    @Schema(description = "신청 시작 일시 (application_starts_at, TIMESTAMPTZ)", example = "2026-09-01T09:00:00+09:00")
    private OffsetDateTime applicationStartsAt;

    @NotNull(message = "신청 종료 일시는 필수입니다.")
    @Schema(description = "신청 종료 일시 (application_ends_at, TIMESTAMPTZ)", example = "2026-09-30T18:00:00+09:00")
    private OffsetDateTime applicationEndsAt;

    @NotBlank(message = "공고 유형은 필수입니다.")
    @Schema(description = "공고 유형 (posting_type, VARCHAR(30))", example = "GENERAL", defaultValue = "GENERAL")
    private String postingType;

    @Schema(description = "혜택 유형 (benefit_type, VARCHAR(30))", example = "청년내일채움공제")
    private String benefitType;
}