package com.gnagnoohc.scms.domain.career.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import com.gnagnoohc.scms.global.common.entity.CommonCode;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

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
    @JoinColumn(name = "ncs_standard_id", nullable = true)
    private NcsStandard ncsStandard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_code_id", nullable = true)
    private CommonCode regionCode;

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
}
