package com.gnagnoohc.scms.domain.counsel.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Confirmed results are corrected by adding a new version instead of overwriting the original. */
@Entity
@Getter
@Table(name = "counseling_public_result", uniqueConstraints = @UniqueConstraint(
        name = "uq_counseling_public_result_session_version",
        columnNames = {"counseling_session_id", "version_no"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CounselingPublicResult extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "public_result_id", nullable = false)
    private Integer publicResultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counseling_session_id", nullable = false)
    private CounselingSession counselingSession;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "result_summary", nullable = false, columnDefinition = "text")
    private String resultSummary;

    @Column(name = "action_plan", columnDefinition = "text")
    private String actionPlan;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "follow_up_data", columnDefinition = "jsonb")
    private JsonNode followUpData;

    @Column(name = "correction_reason", length = 500)
    private String correctionReason;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_by", nullable = false)
    private Integer createdBy;
}
