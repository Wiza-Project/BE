package com.gnagnoohc.scms.domain.career.dto.posting;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 채용공고 단건 상세 조회 응답 DTO
 *
 * <p><strong>[역할 및 데이터 구성 기준]</strong></p>
 * <ul>
 *   <li>공고 상세 팝업/페이지에서 사용
 *   직무 설명({@code jobDescription}) 및 JSON 지원 자격 데이터({@code qualificationData}) 등 모든 세부 데이터를 포함</li>
 *   <li>화면 가독성을 위해 ID형태의 코드/넘버링 형태가 아닌,
 *   기업명({@code companyName}), 직무명({@code ncsCodeName}), 지역명({@code regionCodeName}) 등 조인된 명칭 데이터로 제공 예정</li>
 * </ul>
 */

@Getter
@Builder
@Schema(description = "채용공고 상세 응답 DTO")
public class JobPostingDetailResponseDTO {

    @Schema(description = "채용공고 식별자 (job_posting_id)", example = "10")
    private Integer jobPostingId;

    @Schema(description = "기업 계정 참조 식별자 (company_account_id)", example = "1")
    private Integer companyAccountId;

    @Schema(description = "기업명 (company_account 조인)", example = "(주)위자테크")
    private String companyName;

    @Schema(description = "NCS 공통코드 식별자 (ncs_code_id)", example = "102")
    private Integer ncsCodeId;

    @Schema(description = "NCS 직무명 (common_code 조인)", example = "정보기술 > SW개발")
    private String ncsCodeName;

    @Schema(description = "지역 공통코드 식별자 (region_code_id)", example = "201")
    private Integer regionCodeId;

    @Schema(description = "근무 지역명 (common_code 조인)", example = "서울시 강남구")
    private String regionCodeName;

    @Schema(description = "공통 파일 스토리지 포스터 파일그룹 PK (이미지/PDF 뷰어 렌더링용)", example = "10")
    private Integer fileGroupId;

    @Schema(description = "내부 사용자(검토자) 참조 식별자 (reviewed_by)", example = "5")
    private Integer reviewedBy;

    @Schema(description = "공고 제목 (posting_title)", example = "2026 하반기 Java 백엔드 신입 개발자 채용")
    private String postingTitle;

    @Schema(description = "직무 설명 (job_description, TEXT)", example = "Spring Boot 기반 REST API 개발 및 DB 모델링")
    private String jobDescription;

    @Schema(description = "모집 인원 (recruitment_count)", example = "3")
    private Integer recruitmentCount;

    @Schema(description = "고용 형태 (employment_type)", example = "정규직")
    private String employmentType;

    @Schema(description = "급여 조건 (salary_text)", example = "연봉 4,000만원 이상")
    private String salaryText;

    @Schema(description = "지원 자격 데이터 (qualification_data, JSONB)", example = "{\"major\": \"컴퓨터공학\", \"minGpa\": 3.5}")
    private Map<String, Object> qualificationData;

    @Schema(description = "신청 시작 일시 (application_starts_at)", example = "2026-09-01T09:00:00+09:00")
    private OffsetDateTime applicationStartsAt;

    @Schema(description = "신청 종료 일시 (application_ends_at)", example = "2026-09-30T18:00:00+09:00")
    private OffsetDateTime applicationEndsAt;

    @Schema(description = "공고 유형 (posting_type)", example = "GENERAL")
    private String postingType;

    @Schema(description = "혜택 유형 (benefit_type)", example = "청년내일채움공제")
    private String benefitType;

    @Schema(description = "검수 상태 (review_status)", example = "APPROVED")
    private String reviewStatus;

    @Schema(description = "검토 일시 (reviewed_at)", example = "2026-08-20T14:30:00+09:00")
    private OffsetDateTime reviewedAt;

    @Schema(description = "반려 사유 (rejection_reason, TEXT)", example = "급여 조건 보완 필요")
    private String rejectionReason;

    @Schema(description = "게시 상태 (posting_status)", example = "PUBLISHED")
    private String postingStatus;

    @Schema(description = "제출 일시 (submitted_at)", example = "2026-08-19T10:00:00+09:00")
    private OffsetDateTime submittedAt;

    @Schema(description = "공개 일시 (published_at)", example = "2026-08-20T14:30:00+09:00")
    private OffsetDateTime publishedAt;

    @Schema(description = "생성 일시 (created_at)", example = "2026-08-19T10:00:00+09:00")
    private OffsetDateTime createdAt;

    @Schema(description = "수정 일시 (updated_at)", example = "2026-08-20T14:30:00+09:00")
    private OffsetDateTime updatedAt;

    @Schema(description = "현재 로그인한 학생의 관심공고(스크랩) 등록 여부 (교직원 조회 시 false)", example = "false")
    private Boolean isScrapped;
}