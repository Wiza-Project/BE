package com.gnagnoohc.scms.domain.career.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
import com.gnagnoohc.scms.global.common.entity.FileGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 채용공고 job_posting 엔티티
 *
 * <p><strong>[도메인 라이프사이클]</strong></p>
 * <pre>
 * DRAFT(등록/신청) ──► 교직원 승인 ──► PUBLISHED(게시/학생조회) ──► 기간만료/수동마감 ──► CLOSED(마감)
 * </pre>
 */
@Entity
@Getter
@Table(name = "job_posting")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPosting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_posting_id", nullable = false)
    private Integer jobPostingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_account_id", nullable = false)
    private CompanyAccount companyAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ncs_code_id", nullable = true)
    private CommonCode ncsCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_code_id", nullable = true)
    private CommonCode regionCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_group_id", nullable = true)
    private FileGroup fileGroup;

    @Column(name = "reviewed_by", nullable = true)
    private Integer reviewedBy;

    @Column(name = "posting_title", nullable = false, length = 250)
    private String postingTitle;

    @Column(name = "job_description", nullable = false, columnDefinition = "text")
    private String jobDescription;

    @Column(name = "recruitment_count", nullable = true)
    private Integer recruitmentCount;

    @Column(name = "employment_type", nullable = true, length = 30)
    private String employmentType;

    @Column(name = "salary_text", nullable = true, length = 100)
    private String salaryText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "qualification_data", nullable = true, columnDefinition = "jsonb")
    private JsonNode qualificationData;

    @Column(name = "application_starts_at", nullable = true)
    private Instant applicationStartsAt;

    @Column(name = "application_ends_at", nullable = false)
    private Instant applicationEndsAt;

    @Column(name = "posting_type", nullable = false, length = 30)
    private String postingType = "GENERAL";

    @Column(name = "benefit_type", nullable = true, length = 30)
    private String benefitType;

    @Column(name = "review_status", nullable = false, length = 20)
    private String reviewStatus = "REQUESTED";

    @Column(name = "reviewed_at", nullable = true)
    private Instant reviewedAt;

    @Column(name = "rejection_reason", nullable = true, columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "posting_status", nullable = false, length = 20)
    private String postingStatus = "DRAFT";

    @Column(name = "submitted_at", nullable = true)
    private Instant submittedAt;

    @Column(name = "published_at", nullable = true)
    private Instant publishedAt;

    /**
     * 신규 공고 등록 시에만 사용하는 전용 빌더 생성자
     * (PK, 라이프사이클 상태값 기본, 검토 관련 필드 등 빌더 제외 - 불변성 보장)
     */
    @Builder
    public JobPosting(CompanyAccount companyAccount, CommonCode ncsCode, CommonCode regionCode,
                      FileGroup fileGroup, String postingTitle, String jobDescription, Integer recruitmentCount,
                      String employmentType, String salaryText, JsonNode qualificationData,
                      Instant applicationStartsAt, Instant applicationEndsAt,
                      String postingType, String benefitType) {
        this.companyAccount = companyAccount;
        this.ncsCode = ncsCode;
        this.regionCode = regionCode;
        this.fileGroup = fileGroup;
        this.postingTitle = postingTitle;
        this.jobDescription = jobDescription;
        this.recruitmentCount = recruitmentCount;
        this.employmentType = employmentType;
        this.salaryText = salaryText;
        this.qualificationData = qualificationData;
        this.applicationStartsAt = applicationStartsAt;
        this.applicationEndsAt = applicationEndsAt;
        this.postingType = (postingType != null) ? postingType : "GENERAL";
        this.benefitType = benefitType;
    }

    public void setFileGroup(FileGroup fileGroup) {
        this.fileGroup = fileGroup;
    }

    /**
     * 채용공고 수정 (비즈니스 변경 메서드)
     *
     * <p><strong>[수정 제한 사항]</strong></p>
     * <ul>
     *   <li>기업 계정 식별자({@code companyAccountId})는 최초 등록 후 고정(PK)</li>
     *   <li>NCS 및 지역 코드는 선택 입력(NULL 허용)</li>
     * </ul>
     */
    public void update(CommonCode ncsCode, CommonCode regionCode, FileGroup fileGroup,
                       String postingTitle, String jobDescription, Integer recruitmentCount,
                       String employmentType, String salaryText, JsonNode qualificationData,
                       Instant applicationStartsAt, Instant applicationEndsAt,
                       String postingType, String benefitType) {
        this.ncsCode = ncsCode;
        this.regionCode = regionCode;
        if (fileGroup != null) {
            this.fileGroup = fileGroup;
        }
        this.postingTitle = postingTitle;
        this.jobDescription = jobDescription;
        this.recruitmentCount = recruitmentCount;
        this.employmentType = employmentType;
        this.salaryText = salaryText;
        this.qualificationData = qualificationData;
        this.applicationStartsAt = applicationStartsAt;
        this.applicationEndsAt = applicationEndsAt;
        if (postingType != null) {
            this.postingType = postingType;
        }
        this.benefitType = benefitType;
    }

    /**
     * 교직원 검수 승인/반려 처리 (비즈니스 상태 전이 메서드)
     *
     * <p><strong>[상태 전이 규칙]</strong></p>
     * <ul>
     *   <li><strong>승인 시 ({@code APPROVED}):</strong>
     *     <ul>
     *       <li>{@code review_status} &rarr; {@code APPROVED}</li>
     *       <li>{@code posting_status} &rarr; {@code PUBLISHED} (학생 조회 가능 상태로 전환)</li>
     *       <li>{@code published_at} &rarr; 현재 시각 자동 기록</li>
     *       <li>{@code reviewed_by}, {@code reviewed_at} &rarr; 검수자 식별자 및 일시 정상 기록</li>
     *     </ul>
     *   </li>
     *   <li><strong>반려 시 ({@code REJECTED}):</strong>
     *     <ul>
     *       <li>{@code review_status} &rarr; {@code REJECTED}</li>
     *       <li>{@code posting_status} &rarr; {@code DRAFT} (비공개 유지)</li>
     *       <li>{@code rejection_reason} &rarr; 반려 사유 정상 반영</li>
     *     </ul>
     *   </li>
     * </ul>
     *
     * @param reviewStatus    검수 상태값 ("APPROVED" 또는 "REJECTED")
     * @param rejectionReason 반려 사유 (반려 시 필수, 승인 시 null 가능)
     * @param reviewerId      검수한 교직원 계정 식별자 (Security Context 주입)
     */
    public void review(String reviewStatus, String rejectionReason, Integer reviewerId) {
        this.reviewStatus = reviewStatus;
        this.rejectionReason = rejectionReason;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();

        if ("APPROVED".equalsIgnoreCase(reviewStatus)) {
            this.postingStatus = "PUBLISHED";
            this.publishedAt = Instant.now();
        } else if ("REJECTED".equalsIgnoreCase(reviewStatus)) {
            this.postingStatus = "DRAFT";
        } else if ("CLOSED".equalsIgnoreCase(reviewStatus)) {
            this.postingStatus = "CLOSED";
        } else if ("REQUESTED".equalsIgnoreCase(reviewStatus)) {
            this.postingStatus = "DRAFT";
        }
    }
}