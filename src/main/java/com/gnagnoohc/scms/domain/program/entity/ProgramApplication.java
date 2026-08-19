package com.gnagnoohc.scms.domain.program.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity @Getter
@Table(name = "program_application", uniqueConstraints = @UniqueConstraint(
        name = "uq_program_application_program_student", columnNames = {"program_id", "student_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProgramApplication extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id", nullable = false) private Integer applicationId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "program_id", nullable = false) private ExtracurricularProgram program;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id", nullable = false) private AppUser student;
    @Column(name = "application_status", nullable = false, length = 20) private String applicationStatus = "APPLIED";
    @Column(name = "waitlist_order") private Integer waitlistOrder;
    @Column(name = "processed_by") private Integer processedBy;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "decision_reason", columnDefinition = "text") private String decisionReason;
    @Column(name = "canceled_at") private Instant canceledAt;
    @Column(name = "cancellation_reason", columnDefinition = "text") private String cancellationReason;
    @Column(name = "completion_status", length = 20) private String completionStatus;
    @Column(name = "survey_completed", nullable = false) private boolean surveyCompleted = false;
    @Column(name = "certificate_no", unique = true, length = 80) private String certificateNo;
    @Column(name = "certificate_issued_at") private Instant certificateIssuedAt;
    @Column(name = "judged_by") private Integer judgedBy;
    @Column(name = "completed_at") private Instant completedAt;
}
