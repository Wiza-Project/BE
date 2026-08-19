package com.gnagnoohc.scms.domain.mileage.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import com.gnagnoohc.scms.global.common.entity.FileGroup;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Getter @Table(name = "external_activity_claim")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExternalActivityClaim extends BaseTimeEntity {
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
    @Column(name = "claim_status", nullable = false, length = 20) private String claimStatus = "REQUESTED";
    @Column(name = "reviewed_by") private Integer reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "review_reason", columnDefinition = "text") private String reviewReason;
}
