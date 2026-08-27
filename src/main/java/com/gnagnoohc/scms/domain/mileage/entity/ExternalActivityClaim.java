package com.gnagnoohc.scms.domain.mileage.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import com.gnagnoohc.scms.global.common.entity.FileGroup;
import com.gnagnoohc.scms.global.error.BusinessException;
import com.gnagnoohc.scms.global.error.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** 학생이 증빙 파일과 함께 제출한 외부활동 마일리지 신청을 관리한다. */
@Entity @Getter @Table(name = "external_activity_claim")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalActivityClaim extends BaseTimeEntity {
    public static final String REQUESTED_STATUS = "REQUESTED";
    public static final String APPROVED_STATUS = "APPROVED";
    public static final String REJECTED_STATUS = "REJECTED";
    public static final String CANCELLED_STATUS = "CANCELLED";
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "external_claim_id", nullable = false) private Integer externalClaimId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "file_group_id") private FileGroup fileGroup;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id", nullable = false) private AppUser student;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "activity_type_id", nullable = false) private MileageActivityType activityType;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "mileage_policy_id") private MileagePolicy mileagePolicy;
    @Column(name = "activity_name", nullable = false, length = 200) private String activityName;
    @Column(name = "activity_date", nullable = false) private LocalDate activityDate;
    @Column(name = "requested_points", precision = 10, scale = 2) private BigDecimal requestedPoints;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "detail_data", columnDefinition = "jsonb") private JsonNode detailData;
    @Column(name = "claim_status", nullable = false, length = 20) private String claimStatus = REQUESTED_STATUS;
    @Column(name = "reviewed_by") private Integer reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "review_reason", columnDefinition = "text") private String reviewReason;

    /** 아직 심사하지 않은 신청만 승인한다. 승인 점수는 서비스가 정책에서 확정한다. */
    public void approve(Integer reviewerId, Instant reviewedAt) {
        ensureStatus(REQUESTED_STATUS);
        this.claimStatus = APPROVED_STATUS;
        this.reviewedBy = reviewerId;
        this.reviewedAt = reviewedAt;
        this.reviewReason = null;
    }

    /** 아직 심사하지 않은 신청만 반려한다. */
    public void reject(String reason, Integer reviewerId, Instant reviewedAt) {
        ensureStatus(REQUESTED_STATUS);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "반려 사유는 필수입니다.");
        }
        this.claimStatus = REJECTED_STATUS;
        this.reviewedBy = reviewerId;
        this.reviewedAt = reviewedAt;
        this.reviewReason = reason.trim();
    }

    /** 승인된 신청만 취소한다. 기존 원장은 수정하지 않고 별도 역분개를 생성한다. */
    public void cancel(String reason, Integer reviewerId, Instant reviewedAt) {
        ensureStatus(APPROVED_STATUS);
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "취소 사유는 필수입니다.");
        }
        this.claimStatus = CANCELLED_STATUS;
        this.reviewedBy = reviewerId;
        this.reviewedAt = reviewedAt;
        this.reviewReason = reason.trim();
    }

    private void ensureStatus(String expectedStatus) {
        if (!expectedStatus.equals(claimStatus)) {
            throw new BusinessException(ErrorCode.APPLICATION_ALREADY_PROCESSED);
        }
    }
}
