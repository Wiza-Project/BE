package com.gnagnoohc.scms.domain.competency.entity;

import com.gnagnoohc.scms.domain.user.entity.AppUser;
import com.gnagnoohc.scms.domain.user.entity.UserConsent;
import com.gnagnoohc.scms.global.common.entity.BaseCreatedAtEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity @Getter
@Table(name = "assessment_attempt", uniqueConstraints = @UniqueConstraint(
        name = "uq_assessment_attempt_round_student", columnNames = {"assessment_round_id", "student_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssessmentAttempt extends BaseCreatedAtEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attempt_id", nullable = false) private Integer attemptId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assessment_round_id", nullable = false) private AssessmentRound assessmentRound;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "student_id", nullable = false) private AppUser student;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_consent_id") private UserConsent userConsent;
    @Column(name = "attempt_status", nullable = false, length = 20) private String attemptStatus = "NOT_STARTED";
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "saved_at") private Instant savedAt;
    @Column(name = "submitted_at") private Instant submittedAt;

    // 첫 응답 저장 시점에 한 번만 응시 시작으로 표시한다 (재저장은 시작 시점을 건드리지 않음).
    // AssessmentRoundService.updateRound가 이 startedAt으로 회차 잠금 여부를 판정하므로 반드시 채워야 한다.
    public void start() {
        if (this.startedAt == null) {
            this.startedAt = Instant.now();
            this.attemptStatus = "IN_PROGRESS";
        }
    }

    public void touchSavedAt() {
        this.savedAt = Instant.now();
    }
}
