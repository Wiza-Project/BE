package com.gnagnoohc.scms.domain.career.dto.relation;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 학생 채용 지원 상세 내역 및 전형 진행 상태 응답 DTO
 *
 * <p><strong>[Entity 매핑 기준]</strong></p>
 * <ul>
 *   <li>{@code StudentJobRelation} 원장 데이터 및 연관 {@code JobPosting}, {@code AppUser} 매핑</li>
 * </ul>
 *
 * @author YUN
 */
@Getter
@Builder
@Schema(description = "채용 지원 상세 및 전형 상태 응답 DTO")
public class JobRelationResponseDTO {

    @Schema(description = "학생-공고 관계 식별자 (student_job_relation_id)", example = "100")
    private Integer studentJobRelationId;

    @Schema(description = "채용공고 식별자 (job_posting_id)", example = "1")
    private Integer jobPostingId;

    @Schema(description = "공고 제목 (posting_title)", example = "2026 하반기 신입 백엔드 개발자 채용")
    private String postingTitle;

    @Schema(description = "기업명 (company_name)", example = "(주)카카오")
    private String companyName;

    @Schema(description = "공고 유형 (posting_type, 기본값: GENERAL)", example = "GENERAL")
    private String postingType;

    @Schema(description = "지원 학생 식별자 (app_user.user_id)", example = "1")
    private Integer userId;

    @Schema(description = "지원 학생 학번 (app_user.university_no)", example = "20230001")
    private String universityNo;

    @Schema(description = "지원 학생 성명 (app_user.user_name)", example = "홍길동")
    private String userName;

    @Schema(description = "지원 상태 (APPLIED, DOCUMENT_PASS, FINAL_PASS, REJECTED, CANCELED)", example = "APPLIED")
    private String applicationStatus;

    @Schema(description = "전형 단계 (selection_stage)", example = "1차 서류 심사")
    private String selectionStage;

    @Schema(description = "전형 결과 (selection_result)", example = "PASS")
    private String selectionResult;

    @Schema(description = "추천 경로 (recommendation_source)", example = "STUDENT_DIRECT")
    private String recommendationSource;

    @Schema(description = "잡매칭 적합도 점수 (matching_score)", example = "92.50")
    private BigDecimal matchingScore;

    @Schema(description = "제3자 제공 동의 식별자 (user_consent_id), 미연동 시 null)", example = "501")
    private Integer userConsentId;

    @Schema(description = "지원 일시 (applied_at, KST)", example = "2026-09-01T10:00:00+09:00")
    private OffsetDateTime appliedAt;

    @Schema(description = "지원 취소 일시 (canceled_at, KST)", example = "null")
    private OffsetDateTime canceledAt;
}