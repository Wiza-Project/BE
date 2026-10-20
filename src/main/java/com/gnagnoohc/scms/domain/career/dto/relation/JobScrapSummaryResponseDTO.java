package com.gnagnoohc.scms.domain.career.dto.relation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 학생 관심 공고 스크랩 목록 응답 DTO (D-Day 및 마감 캘린더 연동용)
 *
 * <p><strong>[Entity 매핑 기준]</strong></p>
 * <ul>
 *   <li>{@code StudentJobRelation.bookmarked_at} IS NOT NULL 기준 {@code JobPosting} 조인 조회</li>
 *   <li>{@code ncsCodeName}, {@code regionCodeName}: NcsStandard 및 CommonCodeService를 통해 한글 라벨 바인딩</li>
 * </ul>
 *
 * @author YUN
 */
@Getter
@Builder
@Schema(description = "학생 관심 공고 스크랩 목록 응답 DTO")
public class JobScrapSummaryResponseDTO {

    @Schema(description = "학생-채용 공고 관계 번호 (student_job_relation_id)", example = "100")
    private Integer studentJobRelationId;

    @Schema(description = "채용 공고 번호 (job_posting_id)", example = "1")
    private Integer jobPostingId;

    @Schema(description = "공고 제목 (posting_title)", example = "클라우드 인프라 신입 엔지니어 채용")
    private String postingTitle;

    @Schema(description = "기업명 (company_name)", example = "(주)네이버클라우드")
    private String companyName;

    @Schema(description = "NCS 직무명", example = "클라우드 시스템 관리")
    private String ncsCodeName;

    @Schema(description = "근무 지역명", example = "경기 성남시 분당구")
    private String regionCodeName;

    @Schema(description = "고용 형태 (employment_type)", example = "정규직")
    private String employmentType;

    @Schema(description = "공고 유형 (posting_type)", example = "GENERAL")
    private String postingType;

    @Schema(description = "혜택 유형 (benefit_type)", example = "서류전형 면제")
    private String benefitType;

    @Schema(description = "접수 마감 일시 (application_ends_at, D-Day 계산용, KST)", example = "2026-09-30T18:00:00+09:00")
    private OffsetDateTime applicationEndsAt;

    @Schema(description = "스크랩 일시 (bookmarked_at, KST)", example = "2026-09-01T10:00:00+09:00")
    private OffsetDateTime bookmarkedAt;
}