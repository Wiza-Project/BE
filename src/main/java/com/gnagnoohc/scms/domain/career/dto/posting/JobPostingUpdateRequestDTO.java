package com.gnagnoohc.scms.domain.career.dto.posting;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 채용공고 수정 요청 DTO
 *
 * <p><strong>[데이터 처리 유의사항]</strong></p>
 * <ul>
 *   <li>공고 식별자(PK)는 PathVariable로 전달받으므로 DTO 필드에서 제외</li>
 *   <li>기업 계정 식별자(company_account_id)는 pk라 수정 불가하므로 제외하거나 읽기 전용으로 취급</li>
 *   <li>포스터 변경 시 새로 생성된 file_group_id 전달, 기존 유지 시 기존 ID 유지</li>
 * </ul>
 *
 * @author YUN
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Schema(description = "채용 공고 수정 요청 DTO")
public class JobPostingUpdateRequestDTO {

    @Schema(description = "NCS 공통코드 식별자 (ncs_code_id, NULL 가능)", example = "NCS실제넘버링")
    private Integer ncsCodeId;

    @Schema(description = "지역 공통코드 식별자 (region_code_id, NULL 가능)", example = "지역코드실제넘버링")
    private Integer regionCodeId;

    @Schema(description = "file_group 테이블의 PK (공통 파일 스토리지 포스터/안내문 이미지·PDF 그룹 식별자)", example = "10")
    private Integer fileGroupId;

    @NotBlank(message = "공고 제목은 필수입니다.")
    @Schema(description = "공고 제목 (posting_title)", example = "2026 하반기 Java 백엔드 개발자 채용 (수정)")
    private String postingTitle;

    @NotBlank(message = "직무 설명은 필수입니다.")
    @Schema(description = "직무 설명 (job_description, TEXT)", example = "수정된 직무 상세 요건 내용")
    private String jobDescription;

    @Schema(description = "모집 인원 (recruitment_count)", example = "5")
    private Integer recruitmentCount;

    @Schema(description = "고용 형태 (employment_type)", example = "정규직")
    private String employmentType;

    @Schema(description = "급여 조건 (salary_text)", example = "연봉 4,500만원 이상")
    private String salaryText;

    @Schema(description = "지원 자격 데이터 (qualification_data, JSONB)", example = "{\"major\": \"컴퓨터공학\", \"minGpa\": 3.0}")
    private Map<String, Object> qualificationData;

    @Schema(description = "신청 시작 일시 (application_starts_at)", example = "2026-09-01T09:00:00+09:00")
    private OffsetDateTime applicationStartsAt;

    @NotNull(message = "신청 종료 일시는 필수입니다.")
    @Schema(description = "신청 종료 일시 (application_ends_at)", example = "2026-10-15T18:00:00+09:00")
    private OffsetDateTime applicationEndsAt;

    @NotBlank(message = "공고 유형은 필수입니다.")
    @Schema(description = "공고 유형 (posting_type)", example = "GENERAL")
    private String postingType;

    @Schema(description = "혜택 유형 (benefit_type)", example = "청년내일채움공제")
    private String benefitType;
}